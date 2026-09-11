## 0.2.366-SNAPSHOT

- Migrate `PERF006-B4E — cancellation unwind` to the ratified PLAT021 C-prime control path. Preserve Task-owned cooperative cancellation and cancellation-first Future resume while injecting one exact backend-private cancellation transfer through the already-materialized Bytecode continuation so crossed structured `ensure` cleanup runs before terminalization. Keep the already-honored request shielded across cleanup suspension, preserve nested LIFO cleanup, mark a later escaping cleanup transfer as cancellation supersession before outer handling, and reuse existing post-cleanup structured-child drainage before publishing terminal cancellation. Retain legacy replay cancellation/ensure machinery only as the AST fallback until B6. `while` migration remains B4F. Implementation version becomes `0.2.366-SNAPSHOT`.

## 0.2.365-SNAPSHOT

- Implement `LIB009-A — strict default-profile CSV row parsing and eager parse` (GitHub #349) on the ratified `std:csv/CSV` architecture. Add pure-Protos `parse(text)` returning ordinary `Array<Array<String>>` data with comma/double-quote CSV syntax, doubled-quote escaping, multiline quoted fields, exact CR/LF/CRLF preservation inside quoted fields, CRLF/LF/CR record boundaries outside quotes, blank-record retention, empty/trailing fields, variable row widths, whitespace-as-data and strict malformed-quote rejection. Use only authority-free Core UTF-8/Bytes buffering and logarithmic chunk accumulation internally to keep scanning linear without defining byte-oriented CSV semantics. Export exactly `{parse}`; add no custom dialect API, header meaning, type/null conversion, sniffing, permissive repair, filesystem/network effect, Java production source or native-boundary expansion. Implementation version becomes `0.2.365-SNAPSHOT`.

## 0.2.364-SNAPSHOT

- Ratify `D070 — Language-server executable discovery and public launch surface` (GitHub #348) after explicit project-owner approval and expanded comparison of Gleam, Swift/SourceKit-LSP, Dart Analysis Server, OCaml/ocamllsp, rust-analyzer, gopls, clangd, Haskell Language Server, Zig/ZLS, Eclipse JDT LS, Scala/Metals, Apple Pkl, Enso and Pyright/Pylance-style deployment families. Select Candidate A′: public tool-facing `protos language-server`, launched shell-free through the existing `protos.runtime.executable`, with standard LSP over stdin/stdout and stdout reserved for protocol framing. Keep the configured Protos executable as the single Run/Debug/language-server toolchain authority; add no second server path setting, sibling-path discovery rule, editor-owned server version or Java/JAR/classpath coupling. Preserve Native Image, sibling-binary and self-hosted implementations behind the stable launcher command. Release LM009-F4 only after this ratification publication; no executable CLI/editor implementation, Maven version, specification or LM009-G/H language-intelligence semantics are included.

- Ratify `LIB009-0 — CSV Standard Library design` (GitHub #346) after explicit project-owner approval and exhaustive comparison of RFC 4180/RFC4180-bis direction, CSVW, Python, Go, Rust, Apache Commons CSV, CsvHelper, Ruby, Node CSV, Papa Parse, Swift CodableCSV, NimbleCSV, cassava, DuckDB and Apache Arrow. Select Candidate A: canonical `std:csv/CSV`, strict authority-free CSV syntax over ordinary `String` fields and `Array<String>` rows, an eager `parse` / `encode` convenience surface, and incremental `rowParser` as the scalable foundation. Preserve CR/LF/CRLF text inside quoted fields, retain blank records and variable row widths instead of silently discarding/normalizing data, use CRLF for default writing, and keep headers, types/null/schema, sniffing, permissive repair, spreadsheet policy, Encoding/BOM, filesystem/network authority, global registries and typed/columnar ingestion outside the kernel. Explicit custom-dialect constructor/slot spelling, configured variants, resource-limit API and higher-level tabular conveniences remain deferred. Governance/documentation only: no specification, executable Standard Library/runtime, Maven version or native-boundary change.

- Close `LIB008-D — integrated URI conformance and initial-surface closure` (GitHub #347) after published A/B/C by re-running the complete retained `std:uri` conformance together with the Standard Library resolver and Core native-boundary guards, plus the repository top-level publication-validation gate. Confirm the final public surface remains exactly `{parse, format, resolve}`, the seven-slot ordinary frozen representation and strict RFC 3986 parse/format/section-5 resolution contracts remain intact, A/B/C introduced no normative specification or Java-production/native URI boundary, and all normalization, IRI/IDNA, WHATWG, DNS/Network/filesystem/HTTP, scheme-policy and URI-equivalence work remains deferred. Documentation/governance closure only: no Protos source, Java source/test, public API or Maven implementation-version change.

- Implement `LM009-F3 — dedicated stdio LSP host foundation` (GitHub #340) as the process/protocol edge selected by ratified PLAT024 Candidate A′. Add a thin `com.guillermomolina.protos.lsp` adapter using Eclipse LSP4J 1.0.0 for standard JSON-RPC/LSP framing, one client-session-owned language server, full open/change/close document synchronization into the published F2 snapshot custody, standard shutdown/exit status handling, and an internal JVM stdio process entry point. Advertise no diagnostics, symbols, definition, references, completion, hover or signature capability; LM009-G/H continue to own those semantics. Keep all open buffers in one explicitly non-semantic server-local custody domain: F3 does not infer workspace/package/module identity from editor URIs. Select no public `protos ...` CLI spelling or final packaging contract and add no parser-level cancellation policy. Add focused capability, full-sync, lifecycle and real Content-Length stdio framing tests. Ordinary Protos execution creates no LSP process, threads or analysis state. No specification or Protos language semantics change. Implementation version becomes `0.2.364-SNAPSHOT`.

## 0.2.363-SNAPSHOT

- Implement `LIB008-C — RFC 3986 URI-reference resolution` (GitHub #345) over the published `std:uri` parse/format component model. Add pure-Protos `resolve(base, reference)` with strict RFC 3986 section 5.2 authority inheritance, path merge and section 5.2.4 dot-segment removal, requiring an `absolute-URI` base with no fragment and preserving the reference fragment. Keep explicit-scheme references on the strict absolute branch, preserve encoded spelling outside literal dot-segment processing, validate inputs/results through the existing format/parse syntax authority, and return fresh frozen ordinary seven-slot data. Add the official section 5.4 normal/abnormal corpus plus boundary/failure conformance; export exactly `{parse, format, resolve}`. Add no normalization API, DNS/Network/filesystem/HTTP effect, Java production source or native boundary. Implementation version becomes `0.2.363-SNAPSHOT`.

## 0.2.362-SNAPSHOT

- Migrate `PERF006-B4D — Error handlers` to the ratified PLAT021 C-prime control path. Preserve ordinary `Error.signal` / `Error.handle(body, handler)` lookup and exact Error occurrence identity while selecting/deactivating the innermost Task-local handler token before Bytecode EH resolves crossed cleanup. Execute the canonical standard handler extent as Bytecode `TryCatch`, keep the selected handler removed while its handler Closure runs or suspends, preserve outer-handler search for replacement Errors, and retain the evaluator/replay implementation only as the AST fallback until B6. Cancellation unwind and `while` migration remain B4E/B4F. Implementation version becomes `0.2.362-SNAPSHOT`.

## 0.2.361-SNAPSHOT

- Implement `LM009-F2 — session/workspace snapshot custody` (GitHub #340) on the published F1 parser-authority core. Add one client-session-local `ProtosStaticAnalysisSession` with independent opaque workspace custody domains, concurrent document maps, immutable snapshot capture, exact-value stale-result detection, replacement/removal, and no global semantic state. Document versions remain opaque metadata: F2 deliberately does not invent LSP version-order rules, path/URI/module identity, cancellation semantics, workspace package resolution, background scheduling, or editor-visible diagnostic/navigation policy. Concurrent parsing observes one captured snapshot while later replacement/removal can only make its tagged result stale. Add focused partitioning, stale-result, version-opacity, lifecycle custody and concurrent-independent-document tests. LM009-F remains IN_PROGRESS for the dedicated stdio LSP host and protocol cancellation/lifecycle edge. No specification or Protos language semantics change. Implementation version becomes `0.2.361-SNAPSHOT`.

## 0.2.360-SNAPSHOT

- Implement `LIB008-B — URI format and exact textual round-trip` (GitHub #344) on the ratified `std:uri` seven-component model. Add pure-Protos `format(reference)` over caller-constructed or parsed ordinary component data, preserve exact stored spelling and absent-vs-present-empty delimiters, and validate the recomposed text through the already-published strict RFC 3986 `parse` authority before returning it. Keep serialization linear through authority-free UTF-8/Bytes buffering; export exactly `{parse, format}`; add real-`std:` round-trip, constructed-data and malformed-input conformance. Add no normalization, percent helper, URI prototype/equality law, resolver, DNS/Network/filesystem effect, Java production source or native boundary. Implementation version becomes `0.2.360-SNAPSHOT`.

## 0.2.359-SNAPSHOT

- Implement `LM009-F1 — parser-authority static-analysis core` (GitHub #340) as the first executable consumer of ratified PLAT024 Candidate A′. Add an editor-neutral immutable source snapshot, protocol-neutral parse success/failure results, and `ProtosStaticAnalysisCore` that invokes the real `ProtosParser` directly over unexecuted source. Preserve exact parser `SourceSpan`, message and unexpected-end classification without translating them into LSP diagnostics yet; treat document identity/version as opaque analysis metadata; introduce no Truffle Context, guest execution, module/path guessing, workspace index, global mutable registry, LSP dependency or VS Code semantic implementation. Add focused tests for valid source, unexpected-token and unexpected-EOF failures, plus opaque snapshot metadata. LM009-F remains IN_PROGRESS for session/workspace custody and the stdio LSP host. No Protos specification or public language semantics change. Implementation version becomes `0.2.359-SNAPSHOT`.

## 0.2.358-SNAPSHOT

- Ratify `PLAT024 — Static language-service hosting and protocol boundary` (GitHub #342) after explicit project-owner approval and exhaustive review of all 16 principal + 21 experimental/historical Truffle catalogue entries, including Apple Pkl as the strongest direct precedent, plus focused future-endurance, scalability and Protos-philosophy scoring. Select Candidate A′: a dedicated toolchain-matched Protos static language-server process owned by each editor/client session, standard LSP over stdio as the baseline transport, a thin editor client, and a protocol-neutral analysis core that directly reuses the real parser/source/module/package authorities without guest execution or a Truffle Context. Keep ordinary runtime execution free of language-server overhead, reject a global daemon and editor-side duplicate semantics as the baseline, preserve Graal dynamic LSP as optional augmentation, and retain a future self-hosted/native server behind the same LSP/core boundary. Release LM009-F only after this ratification publication; no specification, executable implementation, Maven implementation version, DAP architecture or editor executable asset changes are included.

- Implement `LIB008-A — URI component data and strict RFC 3986 parse` (GitHub #341) as the first executable slice of the ratified `std:uri` design. Add a pure-Protos strict RFC 3986 URI-reference parser that returns fresh frozen ordinary seven-slot component records, preserves exact accepted scheme/userInfo/host/port/path/query/fragment and percent-triplet spelling, and distinguishes absent components from present-empty components. Validate and decompose generic authority syntax into preserved textual userInfo/host/port fields, including IPv6 and IPvFuture literals, without DNS, scheme policy, normalization, IDNA/IRI or WHATWG repair; keep all parser helpers local so the A-stage module exports exactly `{parse}`. Use the existing authority-free Core UTF-8 Encoding/Bytes mechanism only as an internal linear-time component buffer. Add real-`std:` Protos conformance for accepted forms, malformed syntax, exact component data, frozen ordinary identity, percent escapes, IP literals, arity/domain rejection and exact module exports. No specification, Java production source or native boundary changes. Implementation version becomes `0.2.358-SNAPSHOT`.

## 0.2.357-SNAPSHOT

- Ratify `PLAT023 — Async exact-execution carrier topology for Test Tool`
  (GitHub #335) after explicit project-owner approval and exhaustive review of
  the current Truffle implementation catalogue including Apple Pkl, plus focused
  future-resilience, scalability and Protos-philosophy scoring. Select Candidate
  A′: every exact execution already admitted by bundled-Protos H2B scheduling
  receives one fresh named Java platform Thread; host Submission adds no second
  concurrency limit or TestPlan queue, retains accepted-work custody through
  terminal cleanup, never reuses the production Actor carrier pool, and remains
  replaceable behind the D055 async exact-execution boundary. Preserve D069
  `--jobs N` as logical Test Tool capacity rather than a JVM-thread count; defer
  virtual-thread migration, OS-worker hard kill, remote transport, resources,
  retry/sharding and CPU/NUMA tuning. Release TOOL002-H2B3 without changing
  executable implementation, Maven implementation version, specification or
  native boundary.

- Ratify the refined `LIB008-0 — URI reference Standard Library design` (GitHub #339)
  after explicit project-owner approval. Keep the strict RFC-3986-first,
  synchronous, deterministic and authority-free architecture, and select canonical
  module `std:uri` with fresh behavior-free frozen seven-slot ordinary records
  `{scheme,userInfo,host,port,path,query,fragment}`. `null` means absent while an
  empty String preserves present-but-empty delimiters; generic host/port remain text.
  The initial surface is exactly `parse` / `format` / `resolve`; `resolve` requires
  an RFC `absolute-URI` base (scheme present, fragment absent). No URI-specific
  equality/hash, implicit normalization, percent helper surface, IRI/IDNA, WHATWG,
  DNS/Network/filesystem/HTTP or scheme-specific policy is added. Ordinary-Protos
  UTF-8/Bytes scanning remains an implementation option for linear scaling without
  changing Core. Governance/documentation only: no specification, executable
  Standard Library/runtime, Maven version or native-boundary change.

- Migrate `PERF006-B4C — structured ensure` to the ratified PLAT021 C-prime control path. Preserve ordinary `Object.ensure` lookup/provenance while executing the canonical standard implementation as Bytecode `TryFinally`: validation precedes protected-extent entry, suspension is not unwind, body and cleanup may suspend without replay, normal completion preserves the exact body result, pending Error/non-local-return identity survives cleanup suspension, and a later cleanup transfer supersedes the earlier exit. Keep the evaluator/replay implementation as the AST fallback until B6; Error-handler crossing, cancellation unwind, and `while` migration remain B4D/B4E/B4F. Implementation version becomes `0.2.357-SNAPSHOT`.

## 0.2.356-SNAPSHOT

- Implement the LM009-E D065 / PLAT020 / PLAT022 physical-source correction.
  Keep path-backed source facts backend-neutral until the owning Protos Context
  is entered; admit only each exact already-selected D065 path read-only in that
  Context through a deny-by-default custom filesystem; keep unrelated paths,
  writes and sockets denied; then materialize the physical Truffle Source with
  `canonicalizePath(false)` and the exact already-read characters. Keep
  admission Context-local and thread-safe, remove final Truffle `Source`
  ownership from the resolver payload, preserve virtual/unhosted source paths,
  and require real DAP to present admitted physical Sources without a positive
  `sourceReference`. Preserve PLAT018/D060 debugger topology, module semantics,
  guest filesystem semantics and the VS Code thin-client architecture; live
  LM009-E S3 remains required before closure.

## 0.2.355-SNAPSHOT


- Ratify `D069 — Test Tool parallelism control and default` (GitHub #333) after
  explicit project-owner approval and exhaustive comparison across Apple Swift
  Testing/SwiftPM/Xcode, Rust/libtest, cargo-nextest, Go, pytest/xdist, Node,
  JUnit, Gradle, Maven Surefire, .NET, Elixir ExUnit, CTest, Bazel and Buck2.
  Select `protos test --jobs N` with positive ordinary Integer `N` as global
  logical Test Tool execution-slot capacity and preserve absent `--jobs` as
  `jobs = 1` plus `--jobs 1` as the deterministic serial reduction path. Keep
  numeric jobs independent of JVM threads/CPUs/Processes/workers so later
  resource accounting, OS-worker and remote backends compose without redefining
  the public concept. Defer `jobs=auto`, `-j`, persistent profiles/config,
  resource weights, PLAT023 carrier topology, timeout/retry/sharding/remote
  policy. This governance-only ratification changes no executable implementation,
  Maven implementation version, specification or native boundary.
- Close `LIB007 — Mathematical integer algorithms` through `LIB007-D`
  (GitHub #338) after integrated conformance of the complete ratified
  `std:math/Integer` surface `{gcd,lcm,factorial,pow,powMod}`. Add one real-`std:`
  Protos closure fixture that composes all five operations, retain the complete
  A/B/C boundary/domain/arity/large-value corpus and exact no-helper-leakage
  export assertion, and run repository-selected top-level closure validation.
  Mark the durable LIB007 design record implementation-complete while retaining
  all deferred exclusions. No Standard Library executable source, specification,
  Java production source, Maven implementation version or native boundary changes.

- Advance `PERF006-B4 — control/unwind migration` with bounded B4B non-local return migration under ratified PLAT021 Candidate F. Lower `CanonicalReturn` through the C-prime Bytecode path using the activation's exact live `ProtosReturnHome`, preserve exact return payload identity, consume a transfer only at the prepared invocation that owns that exact home, and carry the same ownership through continuation resume so `^` after a real `Future.value()` suspension cannot escape or replay prior effects. Preserve `InvalidReturn` for missing/completed homes and allow canonical return in already-supported default and composed-expression positions. Do not yet migrate `ensure`, Error handlers, cancellation unwind, or `while`; no Protos specification/public API/global or ThreadLocal control authority changes. Implementation version becomes `0.2.355-SNAPSHOT`.

## 0.2.354-SNAPSHOT

- Ratify `PLAT022 — Context source-readability authority for physical debugger paths` (GitHub #332) after explicit project-owner approval of Candidate D′ and exhaustive Truffle/platform review covering all 16 principal implementations, the historical/experimental catalogue, Graal LSP, Polyglot filesystem machinery, Apple Pkl and mature debugger/source handling. Select one Context-local, deny-by-default read-only authority that admits only exact physical Protos Source paths already selected under D065/PLAT020; keep unrelated files denied, writes and sockets unauthorized, run/debug authority identical, virtual Sources virtual, `ProtosModuleKey`/resolution separate, and already-read characters authoritative. Release the bounded LM009-E implementation slice without changing Protos specification, executable implementation, Maven implementation version, DAP topology, editor protocol or public guest filesystem semantics.

- Implement `LIB007-C — pow / powMod` (GitHub #336) on the ratified
  `std:math/Integer` surface. Add exact `pow(base,exponent)` with binary
  exponentiation and canonical `powMod(base,exponent,modulus)` with
  square-and-multiply plus reduction throughout. Preserve strict ordinary
  unbounded Core `Integer` inputs, reject negative exponents and non-positive
  modular moduli, normalize Core negative remainders to `0 <= r < modulus`, and
  preserve `pow(0,0) == 1` plus `powMod(0,0,m) == 1 mod m` including zero for
  modulus one. Cover exact large powers/modular exponents, signed-base
  normalization, family/lookalike rejection, exact arity and exact
  `{gcd,lcm,factorial,pow,powMod}` exports while retaining A/B regression
  coverage. Add no modular inverse, negative-exponent extension, constant-time
  guarantee, Java production/native math bridge or specification change.
  Implementation version becomes `0.2.354-SNAPSHOT`.

## 0.2.353-SNAPSHOT

- Implement `LIB007-B — factorial` (GitHub #334) on the ratified
  `std:math/Integer` surface. Add exact `factorial(n)` for non-negative ordinary
  unbounded Core `Integer` values, preserving the existing strict family gate and
  using an ordinary-Protos balanced recursive product range so multiplication is
  not permanently shaped as a naive sequential fold. Cover `0!`, `1!`, representative
  values through exact `100!`, negative-domain failure, fixed-width/Float/delegated
  lookalike rejection, exact arity and exact `{gcd,lcm,factorial}` exports while
  retaining LIB007-A regression coverage. Add no arbitrary magnitude cap, Java
  production/native math bridge, specification change, implicit numeric widening,
  power API or backend-specific contract. Implementation version becomes
  `0.2.353-SNAPSHOT`.

## 0.2.352-SNAPSHOT

- Implement `LIB007-A — gcd / lcm foundation` (GitHub #331) as the first
  executable slice of the ratified `std:math/Integer` design. Add ordinary-Protos
  `gcd(a,b)` with sign-normalized Euclidean reduction and `lcm(a,b)` with
  divide-by-GCD-before-multiply, both restricted to exact unbounded ordinary Core
  `Integer` arguments through the existing strict Integer receiver-domain gate.
  Cover zero/sign boundaries, exact arity, fixed-width/Float/delegated-lookalike
  rejection, exact module exports, and values beyond 64-bit range through the
  real `std:` resolver. Add no Java production/native math bridge, specification
  change, implicit numeric widening, `BigInteger` family, factorial/power API or
  cryptographic guarantee. Implementation version becomes `0.2.352-SNAPSHOT`.

## 0.2.351-SNAPSHOT

- Advance `TOOL002-H2` with `H2B2` complete bounded D-case scheduling. Keep
  admission in ordinary bundled Protos while reusing the existing execution,
  closure-wrapper and live-inspection expectation policy: mixed waves may contain
  `executionAsync` and `executionInspectAsync` Futures but share one Protos-owned
  `maxInFlight` bound and one `Future.all(...).value()` wave wait. Evaluate only
  already-rematerialized observations through existing `evaluateDCase` policy and
  retain deterministic TestPlan result order independently of physical completion
  order. Add focal evidence covering simple execution, Future resolution,
  closure-error freshness wrapping, Future terminal failure and stored Future
  observation identity in the same bounded runner. Keep public `protos test`,
  `--jobs`/`jobs=auto`, JVM carrier, resources, hard timeout/kill, retry, remote
  policy, Maven implementation version, specification and native boundary
  unchanged.

- Advance `TOOL002-H2` with bounded `H2B1` simple-case scheduling after four
  unpublished validation attempts exposed invalid nested-suspension compositions.
  Add a Test-Tool-owned bounded kernel in which the current runner Task directly
  fills one wave with `executionAsync` Futures, waits exactly once at the wave
  boundary with `Future.all(...).value()`, and evaluates already-rematerialized
  observations in deterministic TestPlan order. Use the standard suspension-aware
  `while` protocol for the wave loops; create no `Future.then` continuation Task
  and no per-case `Closure.future()` worker in the scheduler. Add executable
  evidence with host capacity above the Protos bound and deliberately reversed
  physical completion order. Keep sequential `runSimple`, public `protos test`
  wiring, future/inspection expectation scheduling, public jobs/default/fairness
  policy, JVM carrier, resource syntax, hard timeout/kill, OS-worker and remote
  policy unchanged. This intermediate Test-Tool-local slice makes no Maven
  implementation-version, specification or native-boundary change.

- Ratify `LIB007-0 — Mathematical integer algorithms Standard Library design`
  (GitHub #329) after explicit project-owner approval and exhaustive comparison
  across Python/CPython, GHC/ghc-bignum, Apple Swift Numerics, Ruby,
  Elixir/BEAM, Smalltalk, Rust, .NET, OpenJDK, Julia/GMP, Go, C++/Boost, GMP and
  Apple Pkl. Select ordinary imported `std:math/Integer` with initial
  `{gcd,lcm,factorial,pow,powMod}` over exact unbounded ordinary Core `Integer`
  only; reject implicit fixed-width/Float widening and a duplicate `BigInteger`
  family; keep algorithms/backend replaceable so Euclid may evolve to
  Lehmer/HGCD and exponentiation/factorial strategies may specialize without API
  change; and give `powMod` no cryptographic constant-time guarantee. Release
  bounded implementation slices only after this durable ratification. No
  specification, executable implementation/runtime, Maven implementation-version,
  package-format, license, release-artifact or deployment change.

- Begin `PERF006-B4 — control/unwind migration` with bounded B4A transfer substrate under ratified PLAT021 Candidate F. Make `ProtosSignalException` a Truffle guest exception while preserving the exact Protos Error occurrence and existing signal identity; classify cooperative Task cancellation alongside non-local return as a Truffle `ControlFlowException`; add a backend-private Bytecode EH envelope that carries only the exact original Protos internal control transfer; and wire the Bytecode root interception plus nested call/resume boundary restoration required for later structured cleanup. Add focused identity/category/isolation evidence. Do not yet lower canonical return or migrate `ensure`, `Error.handle`, cancellation unwind, or `while`; no Protos specification, observable semantics, public API, global/ThreadLocal control authority, or selector-specific control intrinsic changes. Implementation version becomes `0.2.351-SNAPSHOT`.

## 0.2.350-SNAPSHOT

- Ratify `D068 — Exact-SHA documentation extractor execution boundary for
  protos-website` (GitHub #330) after explicit project-owner approval of
  Candidate A-prime and exhaustive comparison with Apple Swift Symbol
  Graph/DocC, Go/pkgsite, Rust/rustdoc/docs.rs, Java/Javadoc, .NET/DocFX,
  Haddock, Dart, Kotlin/Dokka, TypeDoc, ExDoc, Doxygen and Sphinx/autodoc.
  Require WEB001-J7B to run the Protos-owned documentation producer from the
  same exact Protos revision selected by `protos-source.lock.json`, and make the
  existing D064 JSON model the sole durable cross-repository documentation
  contract. Treat the current JDK21/Maven/TOOL003 invocation as replaceable
  producer implementation detail; permit only non-authoritative exact-SHA
  caching; preserve producer-side immutable D064 publication as the future
  multi-consumer scaling path; and add no website parser, independently
  versioned extractor release line, committed generated JSON, mandatory Docker
  ABI, specification change, observable Protos semantic change, Maven
  implementation-version change, Standard Library semantic change or
  deployment-runtime change.

- Ratify `PLAT021 — Bytecode C-prime dynamic control/unwind representation` (GitHub #328) after explicit project-owner approval on 2026-09-11 and exhaustive review of the current Truffle implementation catalogue, including Apple Pkl, plus focused future-durability, scalability and Protos-philosophy scoring. Select Candidate F: keep normal values raw, keep PLAT014/019 suspension distinct from unwind, place resumable structured-control phase in Bytecode continuation state, retain guest Error and internal NLR/cancellation as separate transfer lanes, and use only a narrow backend-private EH bridge when internal control must traverse Bytecode cleanup tables. Preserve exact Error and ReturnHome identity, Task-owned cancellation and dynamic-handler authority, suspendible cleanup with transfer supersession, post-lookup implementation provenance, bounded reusable carriers and no replay/global registry/ThreadLocal authority/universal Outcome tax. Release PERF006-B4 for bounded implementation beginning with B4A transfer substrate. No specification, observable Protos semantics, executable runtime implementation, Maven implementation-version, public API, license term or release artifact change.

- Ratify `PLAT020 — Context-bound Truffle file Source materialization` (GitHub #327) after explicit project-owner approval of Candidate A′ and exhaustive review of the complete Truffle implementation catalogue including Apple Pkl. Keep path/content/module facts immutable and backend-neutral outside Truffle Context ownership; materialize genuine physical file-backed Truffle Sources only inside the owning entered Protos Context through that Context's `Env`, preserving D065 path spelling with `canonicalizePath(false)` and the already-read characters with `.content(...)`. Keep `ProtosModuleKey` semantic identity separate, virtual sources virtual, and add no global source registry, Context-crossing `TruffleFile`, editor/DAP repair layer, cache policy, outer-Polyglot execution refactor, specification change, executable implementation or Maven implementation-version change. Release only the bounded LM009-E D065 source-presentation correction.

- Complete the post-D066/D067 minimum `TOOL003` Standard Library documentation
  extraction path required by WEB001-J7B. Extend the lexer with opt-in line-comment
  observation that leaves ordinary tokenization unchanged, add a
  Standard-Library-only static extractor over the real parser, derive canonical
  `std:` identities from `protos/lib` while excluding physical `core/**`, associate
  D062 `//!` / `///` Markdown, emit the existing D064 deterministic JSON model
  including undocumented D067 entries, and report deterministic missing-doc
  coverage. Generate artifacts on demand from an exact clean Git checkout rather
  than committing a self-referential revision artifact. Add focused extraction,
  placement, callable, naming, determinism and current-stdlib tests. No Protos
  syntax/semantics, Standard Library behavior, visibility/stability policy,
  runtime documentation state, website renderer, package/CLI/IDE/search framework,
  or public CLI command is added.

- Ratify `D067 — Standard Library documentation coverage and API-reference
  publication policy` (GitHub #326) after explicit project-owner approval of
  Candidate D. Keep the complete mechanically observable importable `std:` module
  and top-level-slot inventory in the neutral documentation artifact regardless
  of authored-doc coverage; keep D062 `//!` / `///` prose as an independent
  optional fact; present undocumented observable entries explicitly rather than
  silently hiding them or inferring private/unsupported/unstable semantics; and
  report deterministic missing-documentation coverage without initially making
  it a build failure. Release only the bounded Standard-Library extractor needed
  by WEB001-J7B, preserving D061/D062/D064/D066 and TOOL003-A while adding no
  hide marker, publication manifest, generic package/CLI/IDE/search framework,
  specification change, executable implementation, Maven implementation-version,
  Standard Library semantic, deployment or runtime change.

- Ratify `D066 — Documentation authority, project Wiki, and public website topology`
  (GitHub #325) after explicit project-owner approval and topology re-audit. Keep
  `guillermomolina/protos` as canonical/version-sensitive authority, reserve the
  Protos GitHub Wiki for non-authoritative contributor/project knowledge, and
  retain independent `guillermomolina/protos-website` as public presentation with
  exact-SHA Protos source input. Refine rather than replace WEB001-B; retain
  D061/D062/D064 and published TOOL003-A, abandon the unpublished pre-D066
  TOOL003-B candidate, and require WEB001-J7B to re-derive the smallest
  Protos-owned deterministic Standard Library extraction mechanism needed by its
  real consumer before further cross-consumer generalization. No specification,
  executable implementation, Maven implementation-version, runtime, Standard
  Library semantic, package-format or deployment change.

- Ratify `D065 — File-backed tooling source path identity and canonicalization` (GitHub #323) after explicit project-owner approval and exhaustive cross-language/source-debugging review. Select Candidate B: ordinary filesystem-backed Protos sources expose to tooling the absolute lexically-normalized path by which the current execution/workspace host selected the source, without resolving symlinks solely for presentation identity. Keep `ProtosModuleKey` and package/module rules authoritative for semantic identity; keep generated/in-memory sources virtual; add no alias registry, editor-side path map or DAP proxy; and defer explicit client/target path mapping until a future mode genuinely has distinct namespaces. Release the bounded LM009-E source-presentation correction while preserving D060/PLAT018 debugger architecture. No specification, executable implementation, Maven implementation-version, Standard Library semantic, package-format, release or deployment change.

- Close `GITHUB010 — Exhaustive Dxxx/PLATxxx comparative decision research
  policy` (GitHub #324) by strengthening the pre-approval design gate for future
  substantive decisions. Require broad, materially diverse prior-art research;
  for Dxxx normally compare at least five credible systems across at least three
  distinct approaches; for Truffle-related PLATxxx survey the relevant public
  Truffle implementation space (including Apple Pkl when materially comparable)
  plus mature non-Truffle/OS/runtime evidence where useful. Require every
  surviving candidate to be scored 1–5 with justified confidence across
  correctness/invariants, Protos alignment, future-option resilience,
  scalability, conceptual simplicity, portability/implementation freedom,
  runtime/resource cost, failure/operability, reversibility/migration cost, and
  evidence maturity/implementation risk; require explicit future-regret/escape
  path stress testing and the strongest argument against the recommendation.
  Numeric totals remain advisory rather than authority, and explicit
  project-owner approval remains mandatory. Existing ratified decisions are not
  reopened. No specification, Protos semantics, runtime implementation,
  implementation version, public API, release artifact, or scheduling-priority
  change.

- Begin `TOOL003 — Documentation model and source extractor` (GitHub #322) with
  bounded `TOOL003-A` under ratified D061/D062/D064. Add a reusable
  implementation-neutral documentation model with structural Standard Library
  and package module lineage, stable `(module lineage, top-level slot)` symbol
  identity, separate exact artifact scopes/occurrence keys, normalized
  repository-relative source provenance, optional mechanical callable facts and
  Markdown payload, plus deterministic UTF-8 JSON v1 serialization. Reject
  duplicate semantic identities, absent-module symbol references,
  non-normalized/absolute source paths and invented `std:core` identity; keep
  source coordinates, callable shape, revision/release/content data, UUIDs and
  hashes out of semantic identity. Emit empty article/relationship collections
  until their deferred vocabularies are separately ratified. Add focused tests
  for identity/occurrence separation, source moves, callable evolution,
  duplicate rejection, privacy/path constraints, canonical ordering, Unicode,
  LF normalization and exact final-newline output. Source extraction,
  Standard-Library traversal/artifact generation, API coverage/stability,
  doctests and website rendering remain outside A. No Protos specification,
  observable runtime semantics, Standard Library semantics, package format or
  website change. Implementation version becomes `0.2.349-SNAPSHOT`.

## 0.2.348-SNAPSHOT

- Ratify `GITHUB009 — Native Issue dependency governance and reconciliation`
  (GitHub #321) after explicit project-owner approval of Candidate C-prime. Make
  native GitHub `blocked by` / `blocking` relationships the live authority for
  specific Issue-to-Issue dependencies while keeping Parent/Sub-issue hierarchy,
  `status:*`, `priority:*`, and durable repository evidence orthogonal. Forbid
  inferring dependency edges from blocked status, hierarchy, family, `Triggered
  by`, stale `State at creation` prose, satisfied prerequisites or numbering;
  forbid mechanical hierarchy propagation; preserve closed-blocker dependency
  history; and require explicit coordination to choose a dependent Issue's next
  Status. Keep publication launchers repository-only and defer the reviewed live
  dependency-graph reconciliation to the post-publication GITHUB009 activation
  step. No specification, Protos semantics, runtime implementation,
  implementation version, release artifact or scheduling-priority change.

- Close `PERF001 — Core v0.1 baseline benchmark suite` after PERF001-F retained
  Future/P/Actor reference evidence and PERF001-G final reproducibility/reporting.
  Record F evidence `guillermomolina/protos-benchmarks@f34e37da11f209aa9f9ea84465822c3362fc4da0` from H3 harness
  `b8a9eeca85c241f544512a02a6fa29d935f240ef` and G2 evidence `guillermomolina/protos-benchmarks@45493b49872860f5d29ad3d3a624e0af040c743b` from exact
  G1 harness `2fad6741b429c3e8d69683806e3aadc64f0812cf`. The final bounded exact-pin replay passes D 44/44, E 18/18
  and F 12/12 non-retained 2/2/2 while preserving D/E/F retained timing evidence
  as the sole timing authority; no timing-drift threshold, replacement timing
  corpus, specification, runtime, implementation version, public API, license
  term or semantic/platform decision changes.

- Close `GITHUB008 — Release milestone governance` (GitHub #320) with a narrow
  release-only Milestone contract. Reserve GitHub Milestones for concrete
  project-owner-selected release targets rather than families, implementation
  phases, backlog buckets, Project Roadmap, Status or Priority; keep native
  parent/sub-issue links as hierarchy and Git tags/GitHub Releases as source
  identity/published delivery. Make milestone due dates optional and real,
  require release-significant membership rather than mechanical hierarchy
  propagation, avoid duplicate progress accounting through container parents and
  children, and forbid inferring milestones from family/status/priority/version
  movement. Keep historical prerelease `v0.2.236` without a retrospective
  milestone and deliberately do not create `0.3.0` or any future milestone until
  the project owner selects an actual release target. No specification, Protos
  semantics, runtime implementation, implementation version, release artifact,
  release target, compatibility promise or scheduling priority changes.

- Ratify `D064 — Neutral documentation model schema and stable symbol identity` (GitHub #317) after explicit project-owner approval and exhaustive cross-ecosystem review. Select Candidate G-prime: a compact versioned graph-lite JSON documentation model with durable semantic `SymbolIdentity = (module lineage, top-level slot name)` separated from exact `SymbolOccurrenceKey = (ExactArtifactScope, SymbolIdentity)` and from source/release/content provenance. Keep callable parameter/rest shape as mechanical data rather than identity; treat true module/slot renames as new identities; reject duplicate semantic IDs within one exact artifact; permit only artifact-local numeric indexes as optimizations; require deterministic UTF-8/LF output without timestamps or absolute paths; and allow future sharded/serving formats without redefining symbol identity. Preserve PackageId/version/content separation and defer API coverage, stability/deprecation, doctests, article-ID authoring, rename relations, runtime reflection and website presentation. No specification, runtime/implementation, Maven implementation-version, Standard Library semantic, package-format or deployment change.

- Complete `CLI008-B — value inspection / pretty rendering implementation` with bounded
  `CLI008-B2B` compact/multiline diagnostic layout under ratified D063. Keep structured
  values on one line while their bounded compact diagnostic representation is at most 96
  characters; above that evolvable CLI-only threshold, render Array/Map/IdentityMap,
  ordinary Object, Bytes/ByteRegion and ProcessArguments structures with deterministic
  two-space indentation and explicit line breaks. Wide scalar/String diagnostics remain
  single-line values rather than being reformatted as structure. Preserve the existing
  depth/item/String/output bounds, cycle handling, local-slot-first Object projection,
  specialized-family opacity, non-evaluating behavior, and `inspect != print != serialize`
  separation. Add focused evidence that short structures stay compact, wide structures
  switch deterministically to multiline layout, nested structure is indented, and wide
  scalars remain single-line. `CLI008-B` and `CLI008-B2` are now CLOSED; `CLI008-C`
  remains blocked on PERF006-B4/B5 and no guest-stack capture is included. No specification,
  serialization API, Error-object, `print(...)`, Actor/Process semantics, runtime resource
  behavior, or public compatibility promise for the exact width/indentation is introduced.

## 0.2.347-SNAPSHOT

- Add `GITHUB007 — Issue intake and creation governance` (GitHub #319) to keep
  newly created Issues coherent with GITHUB004–GITHUB006 without reintroducing a
  second Project authority. Add distinct tracked-work, bug, documentation and
  community-request intake forms; give form-created Issues neutral
  `status:inbox` with Priority intentionally unset; keep community reports free
  of formal `family:*` classification until maintainer promotion; and add a
  repository-local intake workflow/helper that derives formal family from an
  authorized identifier, repairs one unambiguous missing native parent, rejects
  parent conflicts, and refuses to promote untrusted identifier-shaped
  submissions. Keep the existing Project status/priority synchronizer as the
  sole Status/Priority projection path. No specification, Protos semantics,
  runtime implementation, implementation version, public API, release artifact,
  license term or design decision changes.

- Begin `LM009-E — VS Code debugging integration` with the explicitly owner-approved Candidate B-prime E1 wiring. Contribute Protos source breakpoints and a launch-only `protos` debugger surface; derive F5 active-file configuration without requiring `launch.json`; retain `program` plus optional string `args` as the persisted launch surface; reuse `protos.runtime.executable`; and have one `DebugAdapterDescriptorFactory` launcher child per session start `protos debug <absolute-file> [args...]` without a shell, consume only D060 version-1 `PROTOS_DEBUG_READY` stdout framing, validate numeric loopback endpoint data, and return `DebugAdapterServer` so VS Code talks directly to the real GraalVM DAP. Add deterministic Node/Python coverage for local/Remote active-file configuration, argument/runtime handling, readiness validation, failure cleanup and concurrent-session isolation. Keep attach, remote listen, readiness files, stop-on-entry, DAP proxying and stronger `terminateDebuggee` behavior out of the baseline. S3 live VS Code evidence remains required before LM009-E closure. No Protos specification/runtime, Maven implementation-version, static language service or Marketplace release change.

- Advance `CLI008-B — value inspection / pretty rendering implementation` with bounded
  `CLI008-B2A` specialized-family inspection coverage under ratified D063. Keep content-backed,
  authority-free sequence values inspectable from already-materialized semantic snapshots:
  `Bytes[...]`, `ByteRegion[...]`, and `ProcessArguments[...]`. Give the current specialized
  capability/reference/resource families stable CLI-only opaque labels instead of `<value>`,
  including ActorRef, GroupRef, send operations, Encoding, Environment, Path, Process,
  Process streams, Filesystem/File, Network, TcpConnection, and TcpListener. Do not expose
  Actor/Group identities, host authority targets, environment contents, native handles,
  transport/resource state, endpoint internals, Java class names, or host `toString()`.
  Preserve the existing diagnostic depth/item/String/output bounds and non-evaluating behavior.
  Add focused evidence for byte-sequence content, resource opacity, Path/Encoding opacity, and
  the REPL `process.args()` snapshot. `CLI008-B` remains `IN_PROGRESS`; `CLI008-B2B` owns the
  remaining compact/multiline pretty-layout reconciliation, while `CLI008-C` remains blocked on
  PERF006-B4/B5. No specification, serialization API, Error-object, stack-capture, `print(...)`,
  Actor/Process semantics, or runtime resource behavior changes.

## 0.2.346-SNAPSHOT

- Begin `CLI008-B — value inspection / pretty rendering implementation` with bounded `CLI008-B1` after ratified D063 Candidate B + S3. Add a dedicated non-evaluating CLI diagnostic inspector while leaving `ProtosValueRenderer` and the standalone `print(...)` path unchanged; route REPL result and existing Error-value presentation through the diagnostic path; render source-like scalars/quoted escaped Strings, bounded Array/Map/IdentityMap values, and ordinary Objects from local slots only; detect cycles; bound depth, item count, String length and total output; and keep Closure/Future values opaque behind stable diagnostic family labels without Java/Truffle names. Add focused unit and REPL evidence that diagnostic Strings remain quoted, ordinary Object inspection shows local state, cycles/truncation are bounded, and `print("hello")`/`print(object)` retain their prior program-output behavior. CLI008-B remains IN_PROGRESS for specialized opaque-family coverage and richer compact/multiline pretty presentation; CLI008-C remains blocked on PERF006-B4/B5. No specification, serialization API, Error-object, stack-capture, Actor/Process semantics or public guest protocol change.

## 0.2.345-SNAPSHOT

- Close `LM009-D — Public debugger launch contract` after D1/D2 publication and final D3 evidence reconciliation. Retain ratified PLAT018 C-prime RuntimeHost/Engine ownership and D060 B-prime `protos debug <file> [args...]` stdout-readiness contract; record production GraalVM DAP availability, OS-ephemeral loopback endpoint discovery, real DAP initialize/launch/configuration flow, application-argument preservation, guest stdout/stderr projection through DAP without control-stream duplication, and normal completion lifecycle through DAP `terminated`, client transport teardown, RuntimeHost/Engine cleanup and launcher exit 0. The earlier D2 test failures are retained as implementation evidence that exposed and corrected Process standard-stream bypass and DAP transport teardown ordering rather than being suppressed. Release LM009-E for the actual VS Code F5/S3 integration. No attach, remote-listen, readiness-file, stop-on-entry or stronger Stop/`terminateDebuggee` contract is selected; no specification, executable runtime, editor asset or Maven implementation-version change is made by this reconciliation.

- Activate `GITHUB006 — Native Issue hierarchy authority and migration closure`
  (GitHub #316) after the project-owner-approved native hierarchy reconciliation
  reports 106 declared parent relationships, 68 already native, 38 added, zero
  conflicts and maximum depth 4. Make native GitHub parent/sub-issue linkage the
  canonical live parent/child coordination structure for formal work; require
  newly created formal child Issues to establish that native relationship at
  creation or immediately afterward; retain textual `Parent:` prose only as
  explanatory/historical context rather than hierarchy authority; and expose
  `Parent issue` plus `Sub-issues progress` as derived Project presentation.
  Preserve the existing durable-granularity rule so mechanical implementation
  phases do not become Issues merely from decomposition. No specification,
  Protos semantics, runtime implementation, implementation version, public API,
  release artifact, license term, status/priority authority, or design decision
  changes.

- Implement `LM009-D2` public debugger launcher/readiness wiring under the ratified D060 B-prime contract. Add `protos debug <file> [args...]` to the ordinary CLI; read/validate the explicit source before debugger startup; preserve ordinary application-argument exclusion of the command/source identity; create the D1 PLAT018 debug RuntimeHost; emit exactly one compact `PROTOS_DEBUG_READY {json}` v1 record on stdout after the OS-allocated loopback endpoint is bound and before the Process Context can execute; retain launch diagnostics on stderr; and route guest stdout/stderr through the currently entered Truffle `Env.out()` / `Env.err()` channels so the real GraalVM DAP output consumer observes them, while null Context sinks prevent duplication onto D060 control pipes. Refactor only the shared standalone bootstrap/binding mechanics needed to keep normal and debug execution on the same Process setup. Add a real-DAP public-CLI launch integration test plus CLI help/failure coverage. VS Code F5 remains LM009-E; no attach/remote-listen/readiness-file/stop-on-entry surface is added. No Protos specification or observable language-semantics change. Implementation version becomes `0.2.345-SNAPSHOT`.

## 0.2.344-SNAPSHOT

- Ratify `D062 — Canonical API documentation authoring convention` (GitHub #313) after explicit project-owner approval and an expanded 21-ecosystem audit including Self, Io, Smalltalk/Pharo, JavaScript, Lua, Go, Rust, Zig, C++, C#, Java, Swift, Kotlin, Haskell, Dart, OCaml, Scala, Ruby, Python, Julia and Elixir. Select Candidate E-prime: `//!` documents the containing module and `///` the immediately following documentable top-level symbol as tooling-only conventions over ordinary Protos `//` comments; ordinary `//`/`/* ... */` remain non-API comments; and canonical supplemental Markdown in `guillermomolina/protos` carries long-form narrative material. Preserve D061 mechanical symbol extraction, avoid duplicate signatures/tags, and defer neutral-model schema, stable symbol IDs, API coverage/publication policy, deprecation vocabulary, doctests, supplemental-article identity and presentation. No specification, runtime/implementation, implementation-version, observable semantics, Standard Library semantics, export/private rule, package format, website or deployment change.

- Ratify `D063 — CLI diagnostic inspection and guest stack presentation contract` (GitHub #314) after explicit project-owner approval of Candidate B + S3 and the additional `inspect != print != serialize` separation. Keep ordinary `print(...)` program output distinct from bounded non-evaluating REPL/CLI inspection; prefer source-like diagnostic forms where natural without requiring universal evaluable/round-trip output; reserve machine interchange for explicit serialization contracts; introduce no nominal `type`; render ordinary objects local-slot-first with deterministic cycle/depth/item/string bounds; and define uncaught guest stacks as guest-only structured metadata owned by the Error transfer/failure occurrence rather than the Error object. Preserve same-Task logical stack across `Future.value()` suspension/resume when C-prime supplies the authority, show async origins only from trustworthy retained provenance, and never fabricate Actor/Process/JVM/Truffle stack continuity. Close CLI008-A, release CLI008-B, and keep CLI008-C dependent on PERF006 B4/B5 or a later PLAT gate only if a new durable host-specific capture decision remains. No specification, runtime implementation, Maven implementation-version, serialization API, Error object, or observable Protos semantic change.

- Ratify `PLAT019 — native semantic suspension bridge into Bytecode C-prime continuations` (GitHub #310) after explicit project-owner approval on 2026-09-10 and exhaustive review of the complete Truffle implementation catalogue (16 principal + 21 experimental/historical entries), including Apple Pkl. Select B-prime: explicit suspension-capable native provenance, explicit resumability before abandoning Java, interpreter-owned PLAT014 C-prime capture, and two-phase Task capture-pending then atomic continuation publication. Deliberately leave the private native-to-interpreter transport representation unspecified. Preserve ordinary native fast paths, Closure extraction/rebinding, lost-wakeup/cancellation/Error semantics, bounded reusable carriers, no replay/global registry/thread identity and B4/B6 ownership boundaries. Release PERF006-B3 for bounded implementation beginning with the two-phase Task publication substrate. No specification, observable semantics, runtime implementation, public API, license term or Maven implementation-version change.

- Ratify `D061 — Standard Library API reference authority and generation model` (GitHub #311) after explicit project-owner approval and an exhaustive comparison of Rust/rustdoc, Go/doc, Java/Javadoc, C# XML documentation, Swift Symbol Graph/DocC, Kotlin/Dokka, Haskell/Haddock, Dart, TypeScript/TypeDoc, Doxygen, Ruby/RDoc, Python, Julia, Elixir/ExDoc and Smalltalk/Pharo. Select Candidate F: derive only mechanically observable module/symbol facts through Protos-owned extraction; keep authored API documentation canonical in `guillermomolina/protos`; validate the two layers; and emit one versioned implementation-neutral documentation model reusable by website, CLI, IDE/LSP and future package documentation. Do not infer stability/contracts/effects/errors from implementation, do not expose `protos/lib/core` as `std:core`, and defer exact doc-comment spelling, symbol-ID/schema and coverage policy to later explicit decisions. Release WEB001-J7B architecture without moving cross-ecosystem extraction into the website. No specification, runtime/implementation, implementation-version, public Standard Library semantics, module/export semantics, package format or deployment change.

- Begin `LM009-D — Public debugger launch contract` implementation with bounded D1 debug-host substrate after ratified PLAT018 C-prime and D060 B-prime. Add a debug-only `ProtosPolyglotRuntimeHost` construction path that enables the real GraalVM DAP on IPv4 loopback port `0`, retains `Suspend=false` + `WaitAttached=true`, and exposes only a validated implementation-neutral bound endpoint through a GraalVM-25.3.4.1-specific readiness adapter. Promote the matching DAP tool from test evidence to runtime availability and include it in the distribution runtime dependency set. Add focused real-DAP/adapter evidence while keeping ordinary RuntimeHosts DAP-disabled. No public `protos debug` command or VS Code F5 integration is included yet; specification and observable Protos semantics are unchanged. Implementation version becomes `0.2.344-SNAPSHOT`.

## 0.2.343-SNAPSHOT

- Close `LM008 — Core Language Surface Completeness` by final `LM008-F` reconciliation after A-E closure: cross-check the retained LM005 concurrent and LM006 system/resource maturity evidence instead of duplicating it; reconcile every direct surface row and the closed I025/I031/I032/I034/I035/I036 implementation findings (including BUG004 as I031-C compatibility fallout); and require the complete central Test Tool corpus plus repository-selected publication validation on the same closure candidate. Close LM008-F and parent LM008. No specification, production/runtime, implementation-version, public API, Core native-boundary, source-style, license or semantic/platform decision change.

- Ratify `D060 — Public debugger launcher and readiness contract` (GitHub #308) after explicit project-owner approval and an extended cross-language debugger audit with focused evaluation of readiness-file rendezvous. Select B-prime: `protos debug <file> [args...]` on the existing launcher plus exactly one versioned `PROTOS_DEBUG_READY {json}` success record on stdout after the PLAT018 loopback endpoint is bound and before guest execution. Keep stderr for launcher/runtime diagnostics, guest stdout/stderr on the real DAP path, one child-stream rendezvous per debug invocation, and no fixed port/global registry/daemon/DAP proxy. Defer readiness files to a future no-config/manual-terminal use case where the IDE is not the launcher parent. Release LM009-D for implementation without selecting VS Code F5 configuration, remote-network/attach/stop-on-entry UX or stronger termination semantics. No specification, runtime implementation or Maven implementation-version change.

- Add `GITHUB005 — automatic Project priority synchronization` (GitHub #309) and close the durable GITHUB004 activation record. Preserve GITHUB001's P0/P1/P2/P3 vocabulary and its explicit-unset default: zero or one `priority:*` label on an open Issue is now the canonical scheduling-priority source, while `Protos Development / Priority` is a derived projection. Extend the existing Project synchronization helper to create missing priority labels during full reconciliation, replace stale competing priority labels, project P0-P3, and clear stale Project Priority when an open Issue is unprioritized. Retain priority labels across closure/reopen as historical scheduling context. No Protos specification, runtime, public API, implementation version, license term, blocker authority, or design decision changes.

- Close `LM008-E — Control, Errors, Modules and Prelude surface audit` (GitHub #103) after E1-E4 final reconciliation. Retain ordinary-Protos lookup evidence for all 51 required standard Prelude bindings, add focused absence regressions for the implementation-only `SmallInteger`/`BigInteger` names, reuse the existing `Boolean`/`P` absence probes, and execute a guest probe proving frozen Prelude bindings reject bare assignment while ordinary local `:` shadowing remains available. Reconcile every E row as covered, intentional absence, or deliberately non-normative without strengthening optional `Bytes`/`Filesystem` availability into a Core absence rule. Release LM008-F for final cross-domain/corpus closure. No specification, production/runtime behavior, implementation version, native boundary, public API, license term or semantic/platform decision changes.

- Implement the ratified PLAT010/PLAT011 Actor carrier architecture for PERF001-F blocker #239: replace the default virtual-thread Actor worker path with bounded platform carriers, make each `ProtosPolyglotRuntimeHost` lazily own one fixed platform-carrier scheduler shared across its hosted Processes, route spawned hosted Actors to that scheduler while preserving staged unhosted/test fallback behavior and exact Process-Context entry, and close carrier resources with RuntimeHost lifetime. Extend focused JVM/Truffle routing evidence to prove two hosted Processes obtain the same scheduler and execute Actor segments on non-virtual platform threads in their own distinct Contexts. No Protos specification or observable Actor semantics change. Implementation version becomes `0.2.343-SNAPSHOT`.

## 0.2.342-SNAPSHOT

- Ratify `PLAT018 — DAP debug-session hosting and endpoint ownership` (GitHub #306) after explicit project-owner approval on 2026-09-10 and extended Truffle/GraalVM debugger review. Select C-prime: one debug invocation owns one `ProtosPolyglotRuntimeHost` / Truffle Engine and the real GraalVM DAP instrument; automatic sessions bind loopback port `0` so the OS allocates the endpoint atomically; Protos emits a stable launcher-owned readiness record while Graal-specific endpoint discovery stays behind a version-bounded adapter; launch staging uses the retained wait-attached/configuration gate; normal F5 uses DAP launch semantics without an editor-side proxy, shared daemon, global port registry or per-Process debugger server. Record the production-distribution requirement for the matching DAP tool and release LM009-D's platform portion while leaving exact CLI spelling, readiness serialization, attach/remote UX and other public tooling policy to LM009-D/E. No specification, observable semantics, runtime implementation, public API, license term or Maven implementation-version change is included in this ratification.

- Add `GITHUB004 — automatic Project status synchronization` (GitHub #307). Make GitHub Issues the canonical machine-readable live work-state source through exactly one `status:*` label on each tracked open Issue, project that state automatically into the user-owned `Protos Development` Project, distinguish `Paused` from `Blocked`, and retain Project priority/area/roadmap as advisory planning metadata. Add an idempotent Issue-event/full-reconciliation workflow that dynamically resolves Project/Status GraphQL IDs and uses a repository Actions secret only for Project access. No Protos specification, runtime, implementation version, public API, license, or design decision changes.

- Close `LM008-D — Values and Core collections surface audit` (GitHub #102) after final D4 reconciliation confirms I034/#272 Array semantic-Integer indexing, I035/#273 single-hash Map insertion, I036/#275 stable Map/IdentityMap association snapshots, and I031/#242 guest-visible structural state are all published. Reclassify every remaining D4 implementation/evidence row `COVERED` and retain three ordinary-Protos keyed-state probes covering closed Map replacement/search/removal ordering, frozen Map fail-fast mutation, and closed/frozen IdentityMap behavior. D1-D4 are complete; parent LM008 remains IN_PROGRESS for E/F. This TEST_IMPACT closure changes no specification, production/runtime behavior, implementation version, native boundary, public API, license term, source-style rule, or semantic/platform decision.

- Close `LM009-C — Run current Protos file` after project-owner S2 validation in a real VS Code Dev Container using the repaired workspace-extension path published at `3fe7ec18`. Confirm the installed `guillermomolina.protos` 0.1.0 extension recognizes `.protos`, exposes `Protos: Run Current File` for a `vscode-remote:` resource, and executes the current source through the real external Protos launcher via the approved VS Code Task + `ProcessExecution` path. Retain the external-launcher authority, source-parent cwd, save-before-run, Workspace Trust, local/remote filesystem boundary and virtual-resource rejection. LM009 remains IN_PROGRESS for DAP/debugging, static language-service and final packaging work. No Protos specification/runtime, Maven implementation version, public DAP contract, static-language-service architecture or Marketplace release change.

- Close `LM008-C — Object structural/reflection/mutation surface audit` (GitHub #101) after current-main reconciliation confirms I031/#242 published all four previously tracked Object surfaces — `slotNames()`, `removeSlot(name)`, structural `close()` and structural `freeze()` — with retained ordinary-Protos conformance. Reclassify every LM008-C matrix row `COVERED`, record I031 A-D/E evidence, release the I031 dependency from the parent audit, and leave LM008-D/LM008-E under their independent owners. The closure reruns focused Object/Array/Map and central Test Tool evidence but changes no specification, production/runtime behavior, implementation version, native boundary, public API, license term, source-style rule, or semantic/platform decision. Parent LM008 remains IN_PROGRESS.

- Repair `LM009-C — Run current Protos file` after S2 exposed that the first tranche incorrectly equated an executable workspace file with URI scheme `file:`. After explicit project-owner approval of the remote workspace-host model, make the VS Code integration a workspace extension, allow Run Current File for local `file:` and VS Code Remote `vscode-remote:` resources, translate remote URI paths to host-native filesystem paths only after constructing a `file:` URI in the workspace extension host, and reject virtual/non-executable schemes instead of fabricating paths. Preserve the external launcher + `ProcessExecution` boundary, source-parent cwd, save-before-run, Workspace Trust, dedicated Task terminal, no shell construction and all DAP/static-language-service/Marketplace exclusions. Add local/remote/remote-Windows-path/virtual-scheme tests. No Protos specification/runtime or Maven implementation-version change; LM009-C remains IN_PROGRESS pending repaired S2 live evidence.


- Close `AUD003` source-style conformance audit after the mandatory execution-time whole-repository E15 rescan confirms the exact reviewed canonical-survivor contracts: C17's 42 conformance files / 66 indexing forms (41 reads, 25 writes), no executable indexing outside that allowlist, four chapter-05 indexing protocol-teaching forms, the exact B1 lazy-Boolean survivors, the exact B2 `not()`/`negated()` survivors, an idiomatic root README and an empty source-style exception manifest. Record that E14 reconciled the two late pre-guard LM008-D4 ordinary `.not()` uses discovered by the first integrated closure attempt. Reconcile the active `SOURCE_STYLE.md` link with the DOC002 role-first AUD003 path and retain the AUD003-D differential prevention gate in publication validation and CI. Top-level publication validation gates closure. No Protos specification, observable semantics, runtime implementation, public API, implementation version, native boundary, performance guarantee or license term changes.

- Close `I031 — Standard Object reflection/mutation/state publication` (GitHub #242) with final cross-slice reconciliation after published A-D evidence for `slotNames()`, `removeSlot(name)`, structural `close()` and structural `freeze()`. Record the durable implementation item as CLOSED while leaving LM008-C/LM008-D to perform their own now-unblocked maturity re-audits. The last I031 implementation-bearing slice remains D (`42f6e0b9`, `0.2.342-SNAPSHOT`, 140 Core native sites / 36 providers); E adds no production/runtime/test/specification change, selector, native Closure site, implementation-version change, source-style exception, or new semantic decision.

- Ratify `PLAT017 — selected-standard Object.call Bytecode intrinsic` (GitHub #304) after explicit project-owner approval on 2026-09-10 and exhaustive review of the complete current Truffle implementation catalogue (16 principal + 21 experimental/historical entries), with deep comparison of Bytecode DSL/SimpleLanguage, TruffleSqueak, Espresso, TruffleRuby, Sulong, GraalJS, GraalPy, FastR, GraalWasm, Enso, SOMns and Pkl. Select ordinary D013 lookup first and intrinsify only the exact canonical standard `Object.call` behavior selected for a Closure target, entering the target Closure directly through the existing PLAT014 C-prime Bytecode composition while preserving shadowing, reflection, receiver/methodHome, ReturnHome lifetime, no-replay suspension and PLAT005/008/013/015 tooling observability. Reject a generic native-to-Bytecode request protocol, a replay-era dual-backend `ProtosClosureInvoker`, and deferral of Closure structural convergence to B6. Release `PERF006-B2D3B` for mechanical implementation subject to the explicit CallTag/source/stack/backtrace/debugger equivalence gate. No implementation/runtime, specification, observable semantics, public API, license term or implementation-version change is included in this ratification.

- Advance `I031-D — structural Object.freeze()` (GitHub #242). Publish the already-normative inherited zero-argument structural freeze operation through the existing Object representation bridge and `ProtosObjectValue.freeze()` primitive: OPEN/CLOSED become FROZEN, repeated freeze remains idempotent, every successful call returns the exact receiver, and freezing is shallow. Add retained ordinary-Protos conformance for exact receiver/idempotence, CLOSED-to-FROZEN hardening, structural creation/removal and existing-slot assignment rejection with preservation, shallow reachability, frozen-root idempotence, frozen Array indexed replacement rejection, frozen Map keyed replacement rejection, and arity rejection. The Core native boundary advances by exactly one construction site in the existing Object provider. No specification, syntax, source-style exception, new runtime family, deep-freeze mechanism, ownership/transfer rule, or second structural-state model is introduced. Implementation version becomes `0.2.342-SNAPSHOT`.

## 0.2.341-SNAPSHOT

- Close DOC002-G9 and the DOC002 role-first documentation migration after the mandatory execution-time rescan proves zero residual flat `docs/project/` records, canonical decision/architecture/registry placement, no active legacy migrated-path references beyond the two E8-documented exact DIST001-E4C2 archive-contract literals, and resolving current navigation links. Retire migration-era wording in AGENTS/docs navigation and make the ratified role-first layout steady-state policy. Historical evidence keeps publication-time paths. No specification, observable semantics, decision, work-item state, implementation/runtime behavior, version, API, registry/blocker meaning, performance guarantee, or license term changes.

- Advance `LM009-C — Run current Protos file` after explicit approval of the launcher-command + VS Code Task `ProcessExecution` design. Add a thin `protos.runCurrentFile` extension-host command that resolves the external launcher from machine setting `protos.runtime.executable` (default `protos` via PATH), saves dirty file-backed Protos documents, runs the absolute source path with `cwd` fixed to its parent through a dedicated/revealed VS Code Task terminal, and blocks execution in Restricted Mode while preserving LM009-B declarative highlighting. Add manifest trust/configuration/command wiring plus editor-local Node/structural validation. Do not scan workspaces for runtimes, reconstruct Java/JAR internals, construct shell commands, add application-argument/package-run/DAP/LSP behavior, publish Marketplace assets, or change Protos specification/runtime/Maven implementation version. LM009-C remains IN_PROGRESS pending real VS Code S2 evidence.

- Advance `I031-C — structural Object.close()` (GitHub #242) and close `BUG004` (GitHub #303). Publish the already-normative inherited zero-argument structural close operation through the existing Object representation bridge and existing `ProtosObjectValue.close()` state primitive: OPEN becomes CLOSED, CLOSED/FROZEN remain idempotent, every successful call returns the exact receiver, and closing is shallow. Add retained ordinary-Protos conformance for exact receiver/idempotence, structural creation/removal rejection with preservation, continued assignment of existing local slots, shallow reachability, frozen-root non-thawing, closed-Array indexed replacement and arity rejection. Reconcile existing owning TextReader/TextWriter/BufferedReader/BufferedWriter capability guards so lookup selecting root `Object.close()` does not masquerade as I/O `Closable`; a nearer resource `close` remains valid under the existing callability/capability-shape rules. The Core native boundary advances by exactly one construction site in the existing Object provider; no extra native site is introduced by BUG004. No specification, syntax, source-style exception, new runtime family, resource-lifecycle semantics or second structural-state model is introduced. Implementation version becomes `0.2.341-SNAPSHOT`.

## 0.2.340-SNAPSHOT

- Migrate DOC002-G8 final residual TOOL002 owner batch into `docs/project/work/TOOL002/`, preserving the exact execution-time Test Tool status/content/authority while reconciling active Markdown references and the two canonical-path literals in the validation-impact name-status parser fixture. Parser/classifier behavior is unchanged. Historical publication-time path spellings remain unchanged. No specification, observable semantics, Test Tool architecture/decision, TOOL002 work/slice/dependency/evidence state, implementation/runtime behavior, version, API, platform architecture, registry/blocker state, or license term changes.

- Migrate DOC002-G7 complete residual TOOL001 owner batch (Package Tool parent plus F2D/F2E records) into `docs/project/work/TOOL001/`, preserving all execution-time status lines, package/tool authority boundaries, decisions, dependencies and evidence while rebasing path/link effects, reconciling maintained active Markdown references, and updating the one active validation-impact docs-path fixture to the canonical TOOL001 parent path without changing classifier behavior. Historical publication-time path spellings remain unchanged. No specification, observable semantics, Package Tool architecture/decision, TOOL001 work/slice/dependency/evidence state, implementation/runtime behavior, version, API, platform architecture, registry/blocker state, or license term changes.

- Close `LM009-B — Language association + syntax highlighting` after project-owner S1 validation in a real VS Code Extension Development Host. Confirm `.protos` recognition as Protos, active `source.protos` highlighting, configured line/block comment actions, and structural delimiter auto-closing on the published `guillermomolina.protos` 0.1.0 manifest surface. Retain the repository-side lexical/manifest validators and document the completed S1 evidence while leaving LM009 itself IN_PROGRESS for run/debug/static-language-service/packaging work. No Protos specification/runtime, Maven implementation version, public DAP contract, static-language-service architecture, Marketplace release, or Node/npm dependency changes.

- Migrate DOC002-G6 residual PERF004 owner batch into `docs/project/work/PERF004/`, preserving the exact execution-time status, non-normative performance-characterization authority, planned slice states, methodology, attribution/closure rules and non-goals while rebasing only path/link effects and reconciling maintained active references. Historical publication-time path spellings remain unchanged. No specification, observable semantics, performance guarantee, PERF004 work/slice/dependency state, implementation/runtime behavior, version, API, platform architecture, registry/blocker state, or license term changes.

- Advance `LM009-B — Language association + syntax highlighting` after explicit approval of the existing `guillermomolina` Marketplace publisher, extension identity `guillermomolina.protos`, independent extension version `0.1.0`, and VS Code compatibility floor `^1.104.0`. Add a declarative `editors/vscode/package.json` binding `.protos` to language id `protos` and the existing `source.protos` TextMate grammar, add minimal comment/bracket `language-configuration.json`, add editor-local manifest validation, and document the live VS Code S1 verification path. Keep the extension code-free in this tranche and leave LM009-B open until actual VS Code-host S1 evidence is recorded. No Protos specification/runtime, Maven implementation version, public DAP contract, static-language-service architecture, VSIX/Marketplace release, or Node/npm dependency is introduced.

- Migrate DOC002-G5 complete residual PERF001 owner batch (benchmark plan plus PERF001-F concurrency methodology) into `docs/project/work/PERF001/`, preserving all execution-time slice states, evidence revisions, workloads, measurement methodology, dependency gates and non-normative authority while rebasing only path/link effects and reconciling maintained active references. Historical publication-time path spellings remain unchanged. No specification, observable semantics, performance guarantee, PERF001 work/slice/evidence state, implementation/runtime behavior, version, API, platform architecture, registry/blocker state, or license term changes.

- Migrate DOC002-G4 complete residual LM008 owner batch (parent plus B/C/D audit records) into `docs/project/work/LM008/`, preserving every execution-time status, classification, dependency, evidence row, decision gate and closure criterion while rebasing only path/link effects and reconciling maintained active references. Historical publication-time path spellings remain unchanged. No specification, observable semantics, LM008 work/slice/dependency state, implementation/runtime behavior, version, API, platform architecture, registry/blocker state, or license term changes.

- Migrate DOC002-G3 residual AUD003 owner batch into `docs/project/work/AUD003/`, preserving its execution-time status, non-normative source-style audit authority, policy/slice/exception evidence and complete content while rebasing only path/link effects and reconciling maintained active references. Historical publication-time path spellings remain unchanged. No specification, observable semantics, AUD003 policy/slice/work-item state, implementation/runtime behavior, version, API, platform architecture, registry/blocker meaning, or license term changes.

- Advance `LM009-B — Language association + syntax highlighting` with the first executable editor-assets tranche. Establish the approved `editors/vscode/` subtree with a non-normative TextMate grammar, representative valid `.protos` lexical fixture, editor-local structural validation and development notes. Highlight only the exact seven Core v0.1 reserved spellings as language-special tokens, keep reserved spellings after `.` ordinary, cover comments, String/escape forms, numeric literal families, ellipsis, delimiters and symbolic operators, and do not invent JavaScript/Python-style keywords. No installable `package.json`, `engines.vscode` support floor, language association, language configuration, Run action, DAP/LSP client, Marketplace policy, specification or runtime semantic change is selected in this tranche. Maven implementation version becomes `0.2.340-SNAPSHOT`.

## 0.2.339-SNAPSHOT

- Migrate DOC002-G2 residual DOC001 owner batch into `docs/project/work/DOC001/`, preserving its IN_PROGRESS/non-normative content and all slice/dependency/closure evidence while rebasing only relative Markdown links and reconciling maintained active references. Historical publication-time path spellings remain unchanged. No specification, observable semantics, DOC001 work/slice state, blocker/dependency state, implementation/runtime behavior, version, API, platform architecture, registry meaning, or license term changes.

- Migrate DOC002-G1 historical DOC002-A audit into `docs/project/work/DOC002/`, preserving the 107-file inventory/Option A decision packet exactly while rebasing only relative Markdown links and reconciling maintained active references. Historical publication-time path spellings remain unchanged. No specification, observable semantics, DOC002 decision outcome, work-item state, implementation/runtime behavior, version, API, platform architecture, registry/blocker state, or license term changes.

- Close DOC002-F after execution-time role placement and legacy-reference audit: all Dxxx decisions, PLATxxx decisions, CORE_* cross-cutting architecture and high-value registries are canonical with no active phase-F legacy path references. Retire completed decision/platform transition wording and hand remaining flat `docs/project/` paths to DOC002-G for final classification/migration reconciliation. No specification, observable semantics, decision outcome, platform/runtime architecture, registry content, blocker/work-item state, implementation/runtime behavior, implementation version, public API, or license term changes.

- Advance `I031-B — Object.removeSlot(name)` (GitHub #242). Publish the already-normative inherited structural mutation through the existing Object representation bridge: validate exactly one semantic String name, require an ordinary-object receiver, remove only that receiver's local slot through the existing OPEN/CLOSED/FROZEN-aware primitive, and return the exact removed object without delegated lookup or coercion. Add retained ordinary-Protos conformance for exact-result identity/delegated reveal, missing/delegated-only rejection, invalid-name no-mutation behavior, frozen-root preservation, represented-value rejection and arity edges; retain focused runtime evidence for CLOSED/FROZEN primitive state. Reconcile the already-modified Core native-boundary architecture guard with DOC002-F4's canonical `docs/project/architecture/CORE_NATIVE_BOUNDARY.md` path so executable validation follows the migrated maintained inventory. The Core native boundary advances by exactly one construction site in the existing Object provider; no specification, syntax, source-style exception, new runtime family or second mutation model is introduced. Implementation version becomes `0.2.339-SNAPSHOT`.

## 0.2.338-SNAPSHOT
\n- Ratify `PLAT016 — Bytecode Closure default-parameter execution topology` (GitHub #295) after explicit project-owner approval on 2026-09-10 and exhaustive maintained-Truffle review including Apple Pkl. Select one Bytecode Closure activation root as the semantic/continuation unit for required binding, omitted-default guest execution, rest binding and body execution over the same `ProtosActivation`; preserve left-to-right existing semantics, supplied-argument suppression, C-prime no-replay suspension, optimizer eligibility and pay-only-for-used default machinery. Permit backend-internal outlining/inlining only when semantically invisible and without new guest identity, tooling change, replay or continuation-policy change. Release `PERF006-B2C3B` for mechanical implementation; no implementation/runtime, specification, observable semantics, public API, license term or implementation-version change is included in this ratification.\n
- Migrate DOC002-F5C durable implementation/closure registry to `docs/project/registries/IMPLEMENTATION_STATUS.md` and reconcile active Markdown references across repository navigation and maintained work records. Preserve the complete execution-time registry content and its authority boundary: durable historical/closure evidence, not live coordination, with normative language authority remaining under `spec/`. No work-item state/closure evidence, specification, observable semantics, blocking state, platform decision, implementation/runtime behavior, implementation version, public API, or license term changes.

- Migrate DOC002-F5B implementation blocker registry to `docs/project/registries/IMPLEMENTATION_BLOCKERS.md`, reconcile active Markdown references including implementation-agent governance, and retire the root AGENTS legacy-path transition example. Preserve the exact Bxxx inventory, blocker states, unblock conditions and non-normative authority. No specification, observable semantics, blocker state, implementation/runtime behavior, implementation version, public API, platform decision, or license term changes.

- Migrate DOC002-F5A platform architecture registry into `docs/project/registries/`, reconcile active Markdown path references, and align the registry/AGENTS Dxxx-role wording with already-ratified DOC002-F0 Option C. Preserve all PLAT001–PLAT015 rows and decision outcomes. No specification, observable semantics, platform architecture, implementation/runtime behavior, blocker state, implementation version, public API, or license term changes.

- Migrate DOC002-F4 cross-cutting Core architecture records `CORE_BOOTSTRAP_ARCHITECTURE.md` and `CORE_NATIVE_BOUNDARY.md` into `docs/project/architecture/`, reconcile active Markdown path references including repository-root/Core-library navigation where present, and preserve historical path spellings. Preserve both records' existing non-normative implementation-architecture/maintenance authority. No specification, observable semantics, Core architecture/native-boundary meaning or count, implementation/runtime behavior, implementation version, public API, or license term changes.

- Migrate DOC002-F3 residual legacy platform decision records PLAT001–PLAT013 and PLAT015 into `docs/project/decisions/platform/`, preserving already-canonical PLAT014 in place. Preserve the existing non-normative authority wording and decision content, reconcile active Markdown path references, and leave the platform registry for its later registry slice. No specification, observable semantics, platform decision outcome, runtime/tooling architecture, implementation, implementation version, identifier, or license term changes.

- Advance `I031-A — Object.slotNames()` (GitHub #242). Publish the already-normative inherited zero-argument reflection operation through the existing Object representation bridge: snapshot receiver-local ordinary-object slot names only, sort them by Unicode scalar-value sequence, and return a fresh open standard Array of semantic Strings on every call, including empty observations of opaque represented Core values. Add retained ordinary-Protos conformance for local-only behavior, prefix/BMP-vs-astral ordering, fresh independent mutable snapshots, empty/represented-value results and arity rejection, using idiomatic bracket indexing required by the repository source-style policy. The Core native boundary advances by exactly one construction site in the existing Object provider; no specification, language syntax, new runtime family, delegated reflection model, or source-style exception is introduced. Implementation version becomes `0.2.338-SNAPSHOT`.

## 0.2.337-SNAPSHOT

- Migrate DOC002-F2 tooling-domain decision records D053/D055/D056/D057 into `docs/project/decisions/tooling/` under the explicitly ratified role-first decision taxonomy. Preserve their existing implementation-independent authority wording and unchanged Core/specification boundary, reconcile active Markdown path references, and preserve historical path spellings. No decision outcome, specification/Core semantics, Package Tool/Test Tool/package-model contract, implementation/runtime behavior, implementation version, platform architecture, identifier, or license term changes.

- Ratify `WEB001-F — Production hosting architecture` (GitHub #294) after explicit project-owner approval: supersede only the GitHub Pages hosting component of WEB001-B with one self-hosted production path based on a generic immutable static website image in the public `guillermomolina/protos-website` repository while environment-specific production deployment configuration remains private and outside the public repositories. Keep exact-SHA Protos source authority, Astro + Starlight, static output, provenance and future playground isolation unchanged; retire the dormant Pages deployment path in the later implementation slice. No website implementation, DNS/production activation, Protos specification/runtime, public API, license term, or implementation-version change.

- Migrate DOC002-F1 language-domain decision records D047/D048/D049/D051/D052 into `docs/project/decisions/language/` and reconcile their legacy authority wording so the repository records are explicitly non-normative while the applicable ratified `spec/` material remains normative. Reconcile active Markdown path references and preserve historical path spellings. No decision outcome, specification revision, language/library semantics, implementation/runtime behavior, public API, implementation version, platform architecture, identifier, license term, or compatibility contract changes.

- Ratify the current `LM009-B` reference-extension topology after explicit project-owner approval on 2026-09-10. Keep the first VS Code integration inside the `guillermomolina/protos` monorepo under `editors/vscode/`, with an editor-local package/version/dependency boundary and no Node/npm dependency for ordinary Maven/runtime execution. Preserve a future split to a dedicated editor repository when independent release cadence or contributor scale earns that coordination cost. Marketplace publisher identity, final VS Code compatibility policy, public DAP lifecycle, static language-service hosting and actual editor implementation remain separate later work. No specification, runtime/editor implementation, public API, license-term, or Maven implementation-version change.

- Ratify DOC002-F0 Option C decision-domain refinement (GitHub #292): add `docs/project/decisions/tooling/` for durable implementation-independent tool/package-system decisions, retain `decisions/language/` for language/specification decision rationale and `decisions/platform/` for host/runtime architecture, and make decision role independent of identifier prefix. No existing decision is moved and no specification, decision outcome, tool/runtime behavior, implementation version, identifier, or license term changes.

- Close `LM009-A — Editor capability baseline` (GitHub #289). Establish `docs/project/work/LM009/` with a bounded IDE-maturity acceptance matrix derived from the closed AUD002/I026 foundation: direct-file CLI execution and real DAP source debugging are existing foundations; VS Code association/highlighting/run/debug packaging, public DAP launch lifecycle, and Protos-specific static diagnostics/navigation remain later LM009 work. Record exact end-to-end reference scenarios and expose the unresolved extension-topology, public-DAP-lifecycle and static-language-service-hosting checkpoints to the normal approval gate without selecting them. No specification, runtime/editor implementation, public CLI, DAP/LSP behavior, license-term, or implementation-version change.

- Close DOC002-E formal work-record migration phase after an execution-time residual audit: remaining flat work records belong only to still-live owner batches and are deferred to DOC002-G residual reconciliation, while Dxxx, PLATxxx, CORE_* and registry records remain reserved for DOC002-F. No file move, specification, implementation, runtime/tooling, platform-decision, license-term or implementation-version change.

- Migrate DOC002-E13 closed I028 work record into `docs/project/work/I028/` under the ratified role-first documentation architecture. Reconcile active Markdown references from the execution-time publication base while preserving historical path spellings, with no networking semantics, Core/Standard-Library API, runtime, platform-decision, specification, license-term, or implementation-version change.

- Close `DOC003 — Documentation branding and approved logo integration` (GitHub #290). Publish the project-owner-approved transparent full Protos logo at `docs/assets/branding/protos-logo.png` and transparent compact symbol at `docs/assets/branding/protos-symbol.png`, record both exact SHA-256 identities and dimensions in the role-first `docs/project/work/DOC003/` closure record, and keep the repository README plus Programming Guide on the canonical full-logo path at their selected display widths. No generated replacement artwork, tagline, website-layout, specification, implementation/runtime, public-API, license-term, or implementation-version change.

- Migrate DOC002-E12 closed I026 owner batch (seven durable Truffle/tooling implementation-evidence records) into `docs/project/work/I026/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/snapshots/prior migration records, and make no specification, platform decision, runtime/compiler/tooling behavior, Process/Actor/P semantics, debugger/DAP/LSP contract, public-API, license-term, or implementation-version change.

- Migrate DOC002-E11 design-closed LIB004 Filesystem / Process Conveniences work record into `docs/project/work/LIB004/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots, and make no specification, filesystem/process convenience semantics, runtime implementation, public-API, license-term, or implementation-version change.

- Close `I026-G2`, parent `I026-G`, and `I026 — Truffle tooling foundation` with bounded real GraalVM 25.3.4.1 LSP evidence. A file-backed Protos `didOpen` reaches successful parse/synchronization, the test records the exact generic capability matrix, and `workspace/executeCommand get_coverage` returns real uncovered StatementTag ranges without guest execution. Completion, hover, signatureHelp, documentHighlight, codeAction and codeLens currently return faithful empty results; definition, references, documentSymbol and workspaceSymbol are explicitly unadvertised. Do not manufacture a top scope, variable tags or tool-owned ProtosActivation to inflate those results. The bounded conclusion is that GraalVM LSP is an experimental transport/sync/parse/instrumentation substrate for Protos, not current runtime-value intelligence or a replacement for Protos-specific static tooling. No production runtime, specification, implementation version, public LSP CLI/port/lifecycle, delegate-server, IDE packaging or production experimental-option policy changes.\n\n- Migrate DOC002-E10 closed LM007 Object Model Maturity work record into `docs/project/work/LM007/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots, and make no specification, object-model semantics, conformance behavior, implementation/runtime, public-API, license-term, or implementation-version change.

- Advance `I026-G — GraalVM dynamic-LSP smoke gate` with evidence-only `I026-G1`. Add the official `org.graalvm.polyglot:lsp` tool POM only to the Maven test classpath at GraalVM 25.3.4.1, start its real registered `lsp` instrument on a loopback ephemeral test-selected port, explicitly opt the test-created Polyglot Context into GraalVM experimental options as required by the experimental `lsp` option, and use raw Content-Length JSON-RPC over TCP to prove `initialize -> initialized -> shutdown -> exit` lifecycle. The initialize capability object is observed but not interpreted as a support claim; G2 retains real Protos document/capability evidence. No production runtime/shaded dependency, specification, implementation version, public Protos LSP CLI/port/lifecycle or production experimental-option policy, delegate-server policy, IDE packaging, static language-server architecture or LSP capability claim changes in G1.

- Close `LIB005-D — integrated closure and conformance` (GitHub #286) and the parent `LIB005 — Networking` work item (#54) after jointly re-running the complete `IpAddresses`/`IpEndpoints` conformance, Standard Library resolver/naming checks and Core native-boundary guard. Historical A1/A2/B/C changed-path verification confirms no `spec/**` or `src/main/**` changes; the distributed initial networking library remains exactly `std:network/IpAddresses` and `std:network/IpEndpoints`, authority-free and without DNS/Resolver/Network/Future/socket acquisition behavior. D is closure/evidence only and does not change the implementation version.

- Migrate DOC002-E9 closed PERF003 collection-algorithm Truffle-compilability work record into `docs/project/work/PERF003/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots, and make no specification, collection/Standard-Library semantics, optimization implementation, compiler/runtime behavior, benchmark-evidence, public-API, license-term, performance-guarantee, or implementation-version change.

- Migrate DOC002-E8 closed PERF002 Truffle-compilability work record into `docs/project/work/PERF002/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots and retain the two DIST001-E4C2 historical runtime-evidence literals that verify the persisted 0.2.236 archive exactly, while making no specification, optimization implementation, compiler/runtime behavior, benchmark-evidence, performance-guarantee, public-API, license-term, or implementation-version change.

- Close `LIB005-C — IpEndpoints parse/format` (GitHub #283). Add ordinary-Protos `std:network/IpEndpoints` with exactly the approved `parse(text)` and `format(endpoint)` surface. Endpoint parsing keeps address grammar single-sourced through `std:network/IpAddresses.parse`, accepts unbracketed IPv4 and bracketed IPv6 only, rejects hostnames/service names and malformed family envelopes, parses only ASCII-decimal ports with mathematical value `1..65535`, accepts decimal leading zeros without adding an unapproved fixed spelling-length limit, and constructs through the existing Core `IpEndpoint(address, port)` factory.
- Canonical endpoint formatting requires `IpEndpoint.recognizes(endpoint)`, delegates nested address spelling to `IpAddresses.format`, emits IPv4 as `address:port` and IPv6 as `[address]:port`, and writes the bounded Core port as decimal without unnecessary leading zeros. Add real-`std:` positive/negative/round-trip conformance and an exact `{parse,format}` module-surface guard. No DNS/Resolver, Network/Future effect, host I/O, shared state, production Java, native Standard Library bridge or specification change is introduced. `LIB005-D — integrated closure` becomes READY.

## 0.2.336-SNAPSHOT

- Close `I026-F — GraalVM DAP smoke gate` with `I026-F2`. Against GraalVM 25.3.4.1, retain a real raw-socket DAP session over file-backed Protos source that discovers the loaded source, proves the source breakpoint reaches verified state (immediately or through GraalVM's resolve event), receives the breakpoint stop, obtains threads and the exact Protos stack location, exposes exactly one PLAT015 activation-native scope with no artificial global/parent/receiver scope, observes representative String/Boolean/Integer values and expands a two-element Array through DAP variables, executes a real `next` from Protos line 1 to line 2, then continues to normal completion and disconnects cleanly. This establishes only the bounded tested GraalVM DAP source-debugging compatibility claim; it adds no public Protos DAP CLI, port/server lifecycle or IDE packaging contract, mutation/evaluation claim, production/runtime source, specification change, or implementation-version change. I026-G remains READY.\n\n- Migrate DOC002-E7 closed DIST002 development/release-toolchain work record into `docs/project/work/DIST002/` under the ratified role-first information architecture. Atomically retarget the portable builder's `runtime_evidence` metadata locator to the new canonical record path, reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots, and make no specification, toolchain coordinate, runtime ABI, CI/runtime/distribution behavior, release-artifact, license-term, or implementation-version change.

- Migrate DOC002-E6 closed LIB006 deterministic-hashing work record into `docs/project/work/LIB006/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots, and make no specification, SHA-256 semantics, implementation/runtime, public API, security-contract, license-term, or implementation-version change.

- Close `LIB005-B — IPv6 parse/format` (GitHub #279). Complete the approved ordinary-Protos `std:network/IpAddresses.parse` / `format` surface for IPv6 while preserving A2 IPv4 behavior. The parser uses fixed eight-entry call-local Array workspaces with explicit logical counts/indexed assignment, matching the existing Core Array protocol rather than assuming an append selector. Accept RFC-4291 numeric forms including one legal `::` compression and final embedded strict dotted-decimal IPv4, reject zones/brackets/whitespace/invalid component counts and spellings, and construct through the existing A1 `v6` helper. Canonical IPv6 formatting uses lowercase hexadecimal, no unnecessary leading zeros, longest zero-run compression with first-run tie breaking and no single-zero compression; IPv4-mapped IPv6 uses the selected `::ffff:a.b.c.d` mixed text while remaining semantic version 6.
- Add focused real-`std:` conformance for full/compressed/mixed IPv6 parsing, canonicalization, all-zero/loopback/max values, zero-run rules, mapped/non-mapped mixed input, semantic round-trip, malformed input and exact unchanged `{v4,v6,parse,format}` public surface. Retire only the two temporary A2 negative expectations that all IPv6 parse/format calls fail. No production Java, specification, DNS/Network/Future/host-I/O, shared state or native-boundary change is introduced. `LIB005-C — endpoint parse/format` becomes READY.

## 0.2.335-SNAPSHOT

- Migrate DOC002-E5 closed LIB003 JSON work record into `docs/project/work/LIB003/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots, and make no specification, JSON semantics, implementation/runtime, public API, license-term, or implementation-version change.

- Migrate DOC002-E4 closed LIB002 Text / Encoding work record into `docs/project/work/LIB002/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots, and make no specification, Encoding/Text I/O semantics, implementation/runtime, public API, license-term, or implementation-version change.

- Migrate DOC002-E3 completed LIB001 Collections work record into `docs/project/work/LIB001/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, preserve historical path spellings in chronology/migration snapshots, and make no specification, collection semantics, implementation/runtime, public API, license-term, or implementation-version change.

- Advance `I026-F — GraalVM DAP smoke gate` with evidence-only `I026-F1`. Add the official `org.graalvm.polyglot:dap` tool POM only to the Maven test classpath at the repository's existing GraalVM version, then start its real registered `dap` instrument on a loopback ephemeral test-selected TCP port. A retained raw socket DAP client proves Content-Length transport and the `initialize -> initialized -> attach -> configurationDone -> disconnect` handshake without depending on internal GraalVM DAP Java APIs. No production runtime/shaded dependency, Protos CLI option, public port/server lifecycle, specification, implementation version, breakpoint/scope/stepping support claim or DAP compatibility claim changes in F1; F2 remains the behavioral gate.

- Migrate DOC002-E2 closed AUD002 work record into `docs/project/work/AUD002/` under the ratified role-first information architecture. Reconcile active Markdown references from the execution-time publication base, retain historical path spellings in frozen/chronological records, and make no specification, implementation/runtime, public API, license-term, or implementation-version change.

- Migrate DOC002-E1 maintained DIST001 work records into `docs/project/work/DIST001/` under the ratified role-first information architecture. Reconcile execution-time Markdown references and relative links, keep immutable DIST001 evidence in `docs/project/evidence/DIST001/`, leave historical path spellings intact where they are chronology/evidence, and make no specification, runtime/implementation, release-record-format, release-artifact, license-term, or implementation-version change.

- Migrate DOC002-D3 immutable DIST001 release evidence into `docs/project/evidence/DIST001/` under the ratified role-first documentation architecture. Preserve all seven evidence blobs byte-for-byte, atomically cut over bounded `dist/*.py` release-tool and test path consumers plus current Markdown references from the execution-time publication base, preserve persisted-record excerpts, leave DIST001 maintained work records for DOC002-E, close DOC002-D, and make no specification, runtime/language implementation, release artifact, public API, license-term, or implementation-version change.

- Close `LIB005-A2 — strict IPv4 parse/format` (GitHub #277). Extend ordinary-Protos `std:network/IpAddresses` with the approved strict numeric IPv4 `parse(text)` and canonical `format(address)` behavior. Parsing accepts only four ASCII decimal octets in `0..255`, rejects legacy abbreviated/octal/hex forms, leading-zero ambiguity, whitespace, Unicode-digit substitution, hostnames and IPv6, and delegates successful construction to the existing A1 `v4` helper. Formatting accepts only recognized standard IPv4 addresses in this slice, decomposes existing Core bits with exact Integer arithmetic, and emits canonical dotted decimal.
- Add real-`std:` conformance for IPv4 boundaries, representative values, parse/format semantic round-trips, malformed/wrong-family rejection and the exact A2 `{v4,v6,parse,format}` module surface. The implementation adds no DNS, Network/Future/host I/O, production Java, native boundary or specification behavior. `LIB005-B — IPv6 parse/format` becomes READY; parent `LIB005` remains IN_PROGRESS.

## 0.2.334-SNAPSHOT

- Close `I036 — Keyed Map iteration snapshot publication`. Standard `Map.each` and `IdentityMap.each` now establish iteration-only immutable association captures containing the exact representative key reference and exact mapped value reference present at snapshot establishment. Search/update/remove keep their existing live Entry snapshots, while iteration no longer aliases later in-place value replacement. Preserve insertion order, shallow object aliasing, same-map mutation during callbacks, nested independent snapshots, polymorphic callability and exact receiver return without introducing locks or persistent per-map snapshot state. Add guest-visible regressions for replacement/removal/reinsertion/insertion during iteration for both Map kinds plus nested independent Map iteration. No specification or native-boundary change; implementation version becomes `0.2.334-SNAPSHOT`.

## 0.2.333-SNAPSHOT

- Close `I026-E2` and parent `I026-E` by retained real GraalVM debugger evidence over the already-published `0.2.330-SNAPSHOT` scope implementation. Discover the Engine's registered `debugger` instrument, start a real `DebuggerSession`, install Protos source breakpoints and prove `SuspendedEvent -> DebugStackFrame -> DebugScope` exposes the exact activation-native read-only scope with no artificial top scope, named receiver or parent-scope hierarchy. Compose this with existing Process-scoped multithread hosting: two carrier threads overlap in one exact `ProtosLanguageContext`, then suspend concurrently at the same breakpoint; both callbacks are simultaneously active and independently observe their `first`/`second` activation-local markers without cross-talk. Release I026-F and I026-G as separate DAP/LSP smoke gates. No production/runtime source, Protos specification, public API, global debugger state or implementation version changes in E2.

- Close `LIB005-A1 — IpAddresses component constructors` (GitHub #274). Publish ordinary-Protos `std:network/IpAddresses` with exactly the approved `v4(a, b, c, d)` and `v6(a, b, c, d, e, f, g, h)` surface. Require exact ordinary unbounded Integer components through the established strict `Integer.div(1)` receiver-domain gate, reject fixed-width/Float/delegated lookalikes and out-of-range values, assemble address bits with exact Integer arithmetic, and delegate successful construction to the existing canonical `IpAddress(version, bits)` Core factory.
- Add real-`std:` Protos-owned conformance for IPv4/IPv6 zero, representative and maximum values, exact recognized state, fresh result identity, and range/family/arity rejection. Because slot-name enumeration is not currently guest-visible, the Java test harness separately inspects the imported module local-slot snapshot to prove the exact approved `v4`/`v6` A1 export surface; it computes no networking behavior. no production Java/native bridge, Network authority, DNS, Future, TCP facade or specification change is introduced. `LIB005-A2` becomes READY; parent `LIB005` remains IN_PROGRESS.

## 0.2.332-SNAPSHOT

- Close `I035 — Map single-hash insertion publication`. Standard Map `atPut` now obtains the query key's current `hash` exactly once, uses that exact mathematical Integer for the insertion search, and reuses the same value as the absent entry's recorded insertion-time hash. Preserve fixed-width hash acceptance, query-to-stored equality direction, representative-key retention, insertion order, open/closed/frozen ordering, same-Map comparison reentrancy and all other keyed operations. Add a guest-visible regression whose hash changes per invocation so callback count and recorded-hash reuse are both observable. No specification or native-boundary change; implementation version becomes `0.2.332-SNAPSHOT`.

## 0.2.331-SNAPSHOT

- Migrate DOC002-D2 retired historical backlog snapshot `OPEN_TASKS.md` into `docs/project/history/` under the ratified role-first documentation architecture. Preserve the frozen snapshot byte-for-byte, reconcile current active documentation references from the execution-time publication base, retain DOC002-A and earlier changelog path spellings as historical evidence, and make no specification, implementation/runtime, public API, scheduling-state, or implementation-version change.

- Ratify `PLAT014 — Truffle cooperative suspension continuation/compilation boundary` after explicit project-owner approval and the completed A1/A2a/A2b/A2c/A2d feasibility sequence. Select C-prime: ordinary Protos execution stays compilation-eligible and captures resumable state only at genuine suspension; single-root Truffle Bytecode DSL continuations compose into a private stackful Protos continuation chain owned by the suspended Task, while bounded carriers remain reusable. Preserve existing Future/Task, Error/handler, `ensure`, cancellation, non-local-return, PLAT005 instrumentation and PLAT008 location identity semantics. A2d retained identical correctness with 4,000 suspensions and 2,048,000 logical operations per sample, 9 successful Truffle compilations, 0 deopts/failures/bailouts and a compiled/non-compiled median ratio of 0.124813. Release the PLAT014 architecture blocker on PERF006 remediation without including production implementation, optimizer-closure wiring, specification changes or an implementation-version change.

- Close `I034 — Array semantic-Integer indexing publication`. Standard Array `at` / `atPut` now accept every semantic Integer family by exact mathematical value, reusing the existing ordinary/fixed represented-value bridge without Float/String conversion, truncation, wrapping or host-width coercion. Preserve receiver-domain, dense bounds, frozen-first mutation validation, exact element/RHS identity, and ordinary bracket lowering. Retain Protos conformance for signed/unsigned fixed-width read/write/bracket success plus negative and UInt64-maximum out-of-range failures. No specification or native-boundary change; implementation version becomes `0.2.331-SNAPSHOT`.

## 0.2.330-SNAPSHOT

- Migrate DOC002-D1 governance records into the ratified role-first `docs/project/governance/` location. Move `LICENSING_RATIONALE.md` and `STANDARD_LIBRARY_NAMING.md` with Git-history-preserving rename detection, reconcile current repository documentation references and relative links from the execution-time publication base, retain the DOC002-A inventory as a historical pre-migration snapshot, and make no specification, implementation/runtime, public API, license-term, or implementation-version change.

- Advance `I026-E — Truffle debugger scope bridge` with `I026-E1` under ratified PLAT015 B′. Export standard Truffle `NodeLibrary` scope access from the current `ProtosExpressionNode` AST only as a bridge to the exact `ProtosActivation` already carried in frame argument 0. Add one synthetic read-only scope adapter whose on-demand member enumeration preserves current-context -> captured-lexical -> receiver/delegation precedence and duplicate shadowed names without guest execution, while `readMember` delegates value selection to ordinary `ProtosActivation.lookup`. Fail closed for null/non-Protos frames and accidental host-only values; expose no artificial language top scope, named `this`/`self`, scope-parent fiction, writes, expression evaluation, Closure execution, global activation registry/cache or debugger lock. Generated instrumentation wrappers inherit the same bridge. E remains IN_PROGRESS; E2 retains real DebuggerSession/same-Context concurrency evidence and final closure. No Protos specification change. Implementation version becomes `0.2.330-SNAPSHOT`.

## 0.2.329-SNAPSHOT

- Close `DOC002-C — navigation foundation` with C2 after C1 propagated the role-first path policy to agents. Establish `docs/project/README.md` and role indexes for work items, language/platform decisions, cross-cutting architecture, governance, registries, evidence, and history without moving or renaming any existing documentation. Keep legacy/straggler paths as transitional compatibility locations, retain execution-time migration discovery and the required final residual reconciliation, and release DOC002-D for bounded low-risk migration. No specification, implementation/runtime, public API, or implementation-version change is introduced.

- Propagate the ratified DOC002 role-first documentation path contract into agent governance with DOC002-C1. Require new durable project records to use role-first destinations while legacy documents remain edited in place until bounded migration; define concurrent-cutover stragglers, execution-time migration discovery, and mandatory residual reconciliation before DOC002 closure. No existing documentation file is moved or renamed, and no specification, implementation/runtime, public API, or implementation-version change is introduced.

- Ratify `DOC002-B — taxonomy ratification and path contract` after explicit project-owner approval of DOC002-A Option A. Select the role-first durable project tree; publish its path and compatibility contract under `docs/project/work/DOC002/`; make the new layout effective for newly created unambiguous durable project records while retaining existing flat paths until bounded migration slices justify relocation. Preserve `spec/` as normative authority and GitHub Issues/Project as live coordination, and make no existing file move/rename, runtime/implementation change, specification change, or implementation-version change.

- Ratify `PLAT015 — Truffle debugger scope projection topology` after explicit project-owner approval and a deep implementation-level review of SimpleLanguage AST/Bytecode DSL, Apple Pkl, TruffleRuby, GraalJS, GraalPy Bytecode DSL, FastR, Sulong and Espresso. Select B′: the exact suspended `ProtosActivation` is the debugger local-scope authority; one synthetic flattened read-only view follows ordinary bare-name precedence and resolves values through the existing activation lookup. Do not invent a Context-wide top scope, `this`/`self`, receiver-as-lexical-parent hierarchy, global activation registry/cache or AST-specific semantic authority. Scope allocation/lifetime remains tooling-request/suspension-bounded and future Bytecode DSL locations are only bridges to the same contract. Release I026-E under PLAT015 without including its implementation. No Protos specification, production implementation or implementation-version change.

- Start `DOC002 — Documentation information architecture and repository reorganization` with bounded audit slice DOC002-A. Add a complete repository-documentation inventory classified by authority, purpose, lifecycle, owning work item, path clarity, link coupling and migration cost; record candidate target taxonomies and a staged migration plan without moving or renaming existing documentation. The recommended target tree remains PROPOSED/NEEDS_USER_DECISION, GitHub Issue #156 remains live coordination authority, `spec/` remains normative, and no implementation/runtime/version change is introduced.

- Close `I026-D7` and parent `I026-D` under ratified PLAT013. The final exhaustive direct-represented-value audit gives Path, Encoding, Environment, ActorRef, GroupRef, Process capability, Network capability and Process standard-stream views an explicit InteropLibrary export containing only bounded host-opaque `Object` display, eliminating the remaining Truffle default host-class/identity display path without exposing IDs, paths, environment contents, authority targets, stream state or other host/runtime internals. A permanent architecture guard requires every direct runtime `ProtosRepresentedValue` to own explicit interop/display coverage; representative runtime evidence proves no accidental member, array, executable or primitive facets. Existing ProtosObjectValue subclasses retain D1's inherited bounded display and exact-class member gate. I026-E becomes READY. Map/IdentityMap hash-entry interop, Closure execution, debugger mutation/evaluation and richer pretty-printing remain explicitly deferred. No Protos specification or observable language semantics change. Implementation version becomes `0.2.329-SNAPSHOT`.

## 0.2.328-SNAPSHOT

- Close `I032 — Fixed-width numeric arithmetic publication` with I032-D final
  reconciliation. Retain ordinary-Protos closure evidence for `+`, `-`, `*`,
  unary negation, `/`, `div`, `mod`, and `%` across all eight fixed-width
  families, every binary cross-family rejection boundary, the remaining
  family-level range/negation edges, and representative strict-arity failures.
  Re-run the native-boundary guard, the central Test Tool corpus, and the
  repository's top-level publication-validation gate. A-C production behavior
  remains unchanged; no Protos specification, production/runtime implementation,
  public API, native boundary, or implementation-version change is introduced.
  I032's last implementation-bearing version remains `0.2.327-SNAPSHOT`; LM008-D2
  is released for separate audit reconciliation. Reconcile the historical
  Developer Makefile work identifier from duplicate `I032` to collision-free
  `I033` / GitHub #267.

- Close `I026-D6 — remaining exact indexed-value Truffle interop` under ratified PLAT013. Extend the already-approved read-only array-like projection to `Bytes`, `ByteRegion`, and immutable Process-argument snapshots using only their existing exact runtime indexing authorities. Bytes/ByteRegion observations use synchronized `indexedSize`/`indexedAt`; ProcessArguments uses its immutable captured indexed representation. Successful reads return exact guest references, accidental host-only values fail closed, mutation and ordinary member facets remain unavailable, implicit iterator derivation is disabled, and bounded family display avoids host leakage. No guest `at`/`size`/`each` invocation, snapshot copy, wrapper graph, debugger lock, Map/IdentityMap hash interop, Closure execution or Protos specification change is introduced. I026-D remains IN_PROGRESS for the final safe-value/display coverage audit. Implementation version becomes `0.2.328-SNAPSHOT`.

## 0.2.327-SNAPSHOT

- Advance `I032 — Fixed-width numeric arithmetic publication` with bounded slice
  I032-C. Publish same-family fixed-width `div` and `mod` through the existing
  family-parameterized representation-bridge construction site: exact quotient
  truncates toward zero, remainder follows the dividend sign, zero divisor is
  rejected before host arithmetic, and every fixed result is range-checked
  before same-family rematerialization. Publish `%` as derived source-backed Core
  behavior on each fixed-width prototype using same-family zero plus `this`
  before `mod`, preserving strict receiver-domain rejection even when an ordinary
  delegator overrides `mod`. Add ordinary-Protos evidence across all eight
  families, signed divisor cases, signed-minimum quotient overflow, zero,
  mixed-family/Integer/Float and delegated receiver/argument boundaries.
  Specification and public syntax are unchanged; native selector surface expands
  by `div`/`mod` while native construction-site/provider cardinality is unchanged.
  I032-D remains the final edge/cross-family reconciliation. Implementation
  version becomes `0.2.327-SNAPSHOT`.

## 0.2.326-SNAPSHOT

- Close `I028-F — cross-slice conformance/native-boundary closure` and parent `I028` without changing the implementation version. Add retained production-path evidence that composes explicit Network provisioning, pre-commit accept cancellation, eight independently pending accepts/connects, Actor/P rejection of live Network/listener/connection authority, simultaneous full-duplex progress, listener-close cutover of a pending accept and deterministic resource teardown. Re-run the retained networking focal surface plus the current Core native-boundary architecture guard and the canonical top-level publication-validation gate. I028-A through I028-F are CLOSED and `LIB005-0` is released for a separate ordinary-Protos Standard-Library design; no DNS/UDP/TLS/HTTP, ambient Network grant, production/runtime, specification, public API or native-boundary change is introduced.

- Close `I026-D5 — Array read-only indexed Truffle interop` under ratified PLAT013. Re-export `InteropLibrary` on the real `ProtosArrayValue` and project only its receiver-owned dense indexed storage: O(1) current size/read, exact stored guest references and cycles, no detached snapshot/wrapper graph, no guest `at`/`size`/`each` dispatch, and fail-closed handling for accidental host-only elements. Preserve D1's no-object-member boundary for Array, keep all interop element mutation unsupported even when the guest Array itself is mutable, explicitly suppress Truffle's default iterator derivation so D5 adds only the authorized indexed facet, and use bounded host-opaque `Array` display. No Protos Array identity, indexed-access, mutation, iteration, lookup or concurrency semantics change. I026-D remains IN_PROGRESS for remaining safe runtime-family facets; Map/IdentityMap hash interop, Closure execution, debugger mutation and scopes remain excluded. No Protos specification change. Implementation version becomes `0.2.326-SNAPSHOT`.

## 0.2.325-SNAPSHOT

- Advance `I032 — Fixed-width numeric arithmetic publication` with bounded slice
  I032-B. Publish same-family fixed-width `/` for all eight fixed-width Integer
  families through the already-existing single family-parameterized
  representation-bridge construction site. Validate exact family membership and
  zero divisor before applying the same exact-rational round-to-nearest-ties-even
  binary64 helper used by ordinary Integer division; return Float without
  fixed-width quotienting or operand conversion. Add ordinary-Protos evidence
  across all families, exact tie-even cases, positive exact zero, zero-divisor,
  mixed-family, ordinary-Integer/Float and delegated receiver/argument rejection.
  Reconcile the stale native-boundary aggregate left after I032-A and add the
  required APL Part 5 License Notice to A's nine new conformance sources without
  changing their bodies. `div`, `mod`, and `%` remain for I032-C. Specification
  and public syntax are unchanged; native selector surface expands but native
  construction-site/provider cardinality is unchanged. Implementation version
  becomes `0.2.325-SNAPSHOT`.

## 0.2.324-SNAPSHOT

- Close `I026-D4 — Float exact binary64 Truffle interop` under ratified PLAT013. Keep the real `ProtosFloatValue` as the interop receiver and project its existing binary64 payload through exact Truffle numeric `fitsIn*/as*` semantics: preserve signed zero in float/double, reject integral projection of `-0.0`, keep NaN/infinities floating-only, reject lossy binary32 conversion, and guard the Java `Long.MAX_VALUE` saturating-cast false-positive. Add bounded value-based host-opaque display plus focused finite/fractional/signed-zero/NaN/infinity/precision/long-boundary/D1-composition evidence. This does not redefine Protos Float family, semantic NaN, signed-zero identity, equality/hash, arithmetic, conversion or coercion behavior. I026-D remains IN_PROGRESS for Array and remaining safe runtime-family facets; Map/IdentityMap hash interop, Closure execution, debugger mutation and scopes remain excluded. No Protos specification change. Implementation version becomes `0.2.324-SNAPSHOT`.

## 0.2.323-SNAPSHOT

- Close `I028-E5 — production Network provisioning/wiring + integrated E closure`
  under D047/D052 and ratified PLAT002/PLAT003/PLAT006/PLAT007/PLAT009. Add one
  lazy RuntimeHost-owned NIO Network plane and an explicit host provisioning
  operation that returns the existing B3 represented Network capability without
  automatically granting Network to any CLI, Process or tool entry. Resolve IPv6
  scope and capture PLAT007 listen addresses inside host Network authority, share
  the bounded NIO substrate across explicitly provisioned capabilities, and release
  all registered network resources when the RuntimeHost closes. Add integrated
  production-path listen/connect/accept/duplex and teardown evidence, close E and
  release I028-F. Specification, public Protos API, native boundary, CLI grant
  policy and poller cardinality/sharding semantics are unchanged. Implementation
  version becomes `0.2.323-SNAPSHOT`.

## 0.2.322-SNAPSHOT

- Close `AUD003-B2 — unary spelling audit` / GitHub #115. Complete the
  ordinary unary-negation cleanup after B2a and concurrent I032-A: migrate the
  remaining Test Tool `result.negated()` use and the three general fixed-width
  positive-arithmetic `negated()` checks to the specified unary `-` surface
  spelling. Retain explicit canonical selectors only where the selector/protocol
  is itself the subject: Boolean `not()` conformance, Integer/Float and fixed-width
  delegated-receiver/error coverage, Integer prototype-local `negated` slot
  visibility, and the Programming Guide/source-style explanatory examples.
  Observable arithmetic/Test Tool behavior, Protos specification, public API,
  native boundary and license terms are unchanged. Implementation version becomes
  `0.2.322-SNAPSHOT`.

## 0.2.321-SNAPSHOT

- Close `I026-D3 — Integer/fixed-width exact numeric Truffle interop` under ratified PLAT013. Keep the real arbitrary-precision `ProtosIntegerValue` and all eight `ProtosFixedIntegerValue` families as the interop receivers; export `isNumber` plus exact `fitsIn*/as*` contracts for byte/short/int/long/BigInteger/float/double. Floating host widths are advertised only when binary conversion preserves the exact mathematical integer, so no tooling-side rounding or implicit coercion is introduced. Add one shared implementation-only exact integral projection helper, value-based host-opaque display and focused boundary/precision/family/D1-composition evidence; reconcile D1's intentionally historical `isNumber == false` assertion now that D3 is the authorized numeric tranche. Float remains separately pending for signed-zero/NaN/infinity handling; Array, Map/IdentityMap hash, Closure execution, debugger mutation and scopes remain excluded. No Protos specification, numeric family, identity/equality/hash, arithmetic or coercion semantics change. Implementation version becomes `0.2.321-SNAPSHOT`.

## 0.2.320-SNAPSHOT

- Advance `I032 — Fixed-width numeric arithmetic publication` with bounded slice
  I032-A. Publish checked same-family `+`, `-`, and `*` for all eight fixed-width
  Integer families through one family-parameterized representation bridge, and
  keep unary `negated` as distributable source-backed Core behavior. Preserve
  strict semantic-family receiver/argument validation and signal `Error` rather
  than wrapping, saturating, promoting, or leaking host-width arithmetic when a
  result is out of range. Retain ordinary-Protos coverage for successful
  operations, overflow/underflow, signed/unsigned negation, mixed-family
  rejection and delegated-receiver rejection. Fixed-width `/`, `div`, `mod` and
  `%` remain for later I032 slices. Specification and public syntax are unchanged.
  The audited Core native boundary grows by one generic representation-bridge
  construction site/provider. Implementation version becomes `0.2.320-SNAPSHOT`.

## 0.2.319-SNAPSHOT

- Close corrective `I026-D2A — simple scalar bounded display` under ratified PLAT013 after the post-D2 audit found that Truffle's default guest-object `toDisplayString` exposes receiver class name plus identity hash. Give the already-published real String/Boolean/null receivers direct side-effect-free host-opaque display: String returns its existing payload, Boolean canonical `true`/`false`, and null canonical `null`. Add focused no-host-leakage evidence while preserving D2 scalar facets and introducing no guest lookup, invocation, wrapper graph, numeric/Array/Map/Closure capability or debugger mutation. I026-D remains IN_PROGRESS; numeric projection is next. No Protos specification or observable language semantics change. Implementation version becomes `0.2.319-SNAPSHOT`.

## 0.2.318-SNAPSHOT

- Close `I028-E4 — production TcpListener/accept + PLAT007 IPv6-only backend` under
  D047/D052 and ratified PLAT003/PLAT006/PLAT007. Extend the existing NIO Network
  authority target with asynchronous `listenTcp` acquisition, IPv4-only `INET`
  listening, exact-address IPv6 listening only where public-JDK family semantics
  can be preserved, and the PLAT007 `address: null` IPv6 composite over concrete
  Network-authorized IPv6 addresses sharing one acquired port. Add one logical
  poller-owned listener backend with multiple independently pending accepts,
  accepted TcpConnection E3 backend handoff, explicit cancellation/late-resource
  custody, all-or-nothing composite construction and whole-listener close.
  Specification, public Protos API, native boundary, endpoint identity and
  poller-count/sharding policy are unchanged. Implementation version becomes
  `0.2.318-SNAPSHOT` and E5 is released.

## 0.2.317-SNAPSHOT

- Close `I026-D2 — String/Boolean/null read-only Truffle scalar interop` under ratified PLAT013 C′. Keep the existing real `ProtosStringValue`, `ProtosBooleanValue` and `ProtosNullValue` as the interop receivers and export only their exact side-effect-free scalar facets (`isString/asString`, `isBoolean/asBoolean`, `isNull`), with no wrapper graph or host-value conversion. Add focused evidence for facet exclusivity and composition with D1 local-member reads while leaving numeric, Array, Map/IdentityMap hash, Closure execution, debugger mutation and scope topology outside the slice. I026-D remains IN_PROGRESS and I026-E remains dependency-gated. No Protos specification, identity/equality/hash, lookup, mutation or concurrency semantics change. Implementation version becomes `0.2.317-SNAPSHOT`.

## 0.2.316-SNAPSHOT

- Close `I026-D1 — ordinary Object read-only Truffle interop` as the first executable PLAT013 C′ tranche. Make real `ProtosObjectValue` instances direct `InteropLibrary` receivers without a debugger wrapper graph and mark the existing implementation-only `ProtosRepresentedValue` family as bare opaque `TruffleObject` values so semantic slot contents satisfy the interop value contract without yet exporting primitive facets. Exact ordinary Objects enumerate/read only current local slots through a small immutable member-name array adapter, preserve specialized values/cycles as the same guest value, fail closed on accidental host-only slot contents, reject delegated lookup and all member mutation, and use bounded host-opaque display. Keep inherited Array/Map/Closure/Future/resource families out of this member tranche so their facets remain independently auditable. I026-D stays IN_PROGRESS for the remaining approved runtime-family facets; I026-E remains dependency-gated. No Protos specification, identity/equality/hash, lookup, mutation or concurrency semantics change. Implementation version becomes `0.2.316-SNAPSHOT`.

## 0.2.315-SNAPSHOT

- Close `AUD003-B1 — lazy Boolean spelling audit` / GitHub #114. Complete the
  B1a/B1b migration of ordinary explicit parameterless-Closure and trailing-
  Closure `and`/`or` spellings to idiomatic `&&`/`||`, preserve direct Boolean-
  protocol conformance evidence, and retain the chapter-04 explicit forms that
  intentionally teach lazy Boolean protocol/Closure behavior. Normalize the
  final ordinary equality example in chapter 05 and close B1 on fail-closed
  executable-source and Programming Guide rescans. Documentation/source-style
  only: no Protos specification, runtime semantics, implementation version,
  public API, native boundary, or license terms change.

- Close `I033 — Developer Makefile workflow` with a small self-documenting repository-root developer interface. Keep the existing `build`, `test`, and `dist` command contracts, add `.PHONY`, `help` as the safe default, overridable Maven/Python/shell flags, and focused `toolchain`, `compile`, `check`, `verify`, `clean`, and `dist-validate` targets. The Makefile delegates policy to the existing Maven/toolchain/distribution machinery rather than duplicating CI or PERF007 routing, and does not mask test compilation with `maven.test.skip`. No Protos semantics, public API, implementation version or license terms change.

- Close `I028-E3D — directional shutdown/close + integrated E3 closure` under the
  existing Closable/ReadShutdown/WriteShutdown contracts and ratified
  PLAT003/PLAT006/PLAT009. Map logical half-closes to poller-owned
  `SocketChannel.shutdownInput()` / `shutdownOutput()`, preserve the opposite
  duplex lane and retain whole-resource close as the stronger physical-custody
  release. Correct generic ByteIo read admission so whole close dominates a prior
  local read shutdown, add real loopback half-close/close evidence, close E3 and
  release E4. Specification, public Protos API, native boundary, poller policy and
  license terms are unchanged. Implementation version becomes `0.2.315-SNAPSHOT`.

## 0.2.314-SNAPSHOT

- Make the owning GitHub Issue `family:*` label the single source of truth for formal work-family classification. Treat any custom `Family` field in the `Protos Development` Project as redundant, non-authoritative presentation state that routine agents must not populate or synchronize; Project views that need family visibility should display the standard Issue `Labels` field instead. This does not require agents to inspect or mutate GitHub Projects. Governance only: no Protos specification, executable implementation, implementation version, or license terms change.

- Close `I028-E3C — NIO write lane + PLAT009 partial-write arbitration` under
  D047/D052, ByteWritable and ratified PLAT003/PLAT006/PLAT009. Add one
  poller-owned ordered NIO write request, immediate/`OP_WRITE` partial progress,
  first-attempt zero/positive arbitration through the E3A gate, hidden
  contributed-prefix failure accounting and pre-first-byte cancellation retirement.
  Preserve independent `OP_READ`, retain backend custody after first contribution,
  add loopback delivery/partial-cancellation/full-duplex evidence and release E3D.
  Specification, public Protos API, native boundary, poller policy and license terms
  are unchanged. Implementation version becomes `0.2.314-SNAPSHOT`.

## 0.2.313-SNAPSHOT

- Stop requiring routine agents to reconcile GitHub Project metadata during formal
  Issue allocation, activation, blocking, review, publication, or closure. Keep
  GitHub Issues, `family:*` classification, assignee discipline, and Issue work
  logs as the required agent-facing coordination surfaces when their actions are
  available. Treat `Protos Development` Project membership and `Status`, `Area`,
  `Roadmap`, `Priority`, `Owner`, and similar fields as optional scheduling/
  presentation metadata; agents must not probe for Project capabilities or report
  their absence as missing coordination unless the project owner explicitly asks
  for a Project operation and a documented Project-capable action already exists.
  Governance only: no Protos specification, executable implementation,
  implementation version, or license terms change.

- Close `PERF005 — Protos test-corpus execution acceleration` / GitHub #244 after the measurement-first A/B/C program. Retain conservative diff-driven impact-aware publication validation: unequivocally Package Tool-local candidates run the complete mapped Package affected set, unequivocally Test Tool-local candidates run the complete Test Tool affected set, and shared/unknown/cross-tool or top-level closure candidates fail closed to the complete Maven suite. Current-baseline B3 evidence measured `107.281 s` FULL, `92.634 s` Package-local median (`13.7%` improvement), and `24.165 s` Test Tool-local median (`77.5%` improvement), with all routed runs green and stable cardinality. Keep `scripts/publication_validation.py` as a temporary host-side execution bridge until the official `protos test` / TOOL002 path demonstrably owns the same publication-validation contract; the deterministic impact-routing policy may outlive that bridge. TOOL002-H remains suspended. No Protos specification, runtime semantics, Maven implementation version, or license terms change in this final reconciliation.

- Ratify `PLAT013 — Truffle debugger/interop value projection architecture` / GitHub #250 after explicit project-owner approval and exhaustive Truffle-language (including Apple Pkl), Bytecode-DSL, identity, large-object-graph and scalability review. Select C′: real Protos runtime values are the authoritative read-only semantic-minimum interop receivers; ordinary object members project local slots only; synthetic scopes/contextual views remain adapters. Defer delegated lookup, Closure execution, debugger mutation and initial Map/IdentityMap hash-entry interop. Release I026-D as READY under PLAT013. Governance/platform-architecture only: no Protos specification, executable implementation or implementation-version change.

- Ratify `PLAT012 — Verified external package custody and source-resolution architecture` / GitHub #248 after explicit project-owner approval and exhaustive cross-runtime/package-store review. Select a run-owned exact immutable package-resource scope with 1:1 detached-plan/custody reconciliation and lazy host-neutral reads over the same F2E2-verified backing; canonical external ModuleKey remains exact package identity + internal logical module, while aliases, URLs, paths, Filesystems, custody objects and loader-domain identity remain excluded. Preserve optional lazy caching as tuning and future NIO/memory/mmap/CAS/brokered/distributed backing evolution. Clear the architecture gate for `TOOL001-F2E4`; F2E5 still owns public run lifecycle integration. Governance/platform-architecture only: no Core specification, executable implementation, Maven version, lock format, PackageExecutionPlan ABI or public run change.

- Close `I028-E3B — NIO read lane + independent readiness` under the existing
  ByteReadable contract and ratified PLAT003/PLAT006/PLAT009. Extend the connected
  NIO TcpConnection backend with one poller-owned read request, immediate
  non-blocking `SocketChannel.read`, `OP_READ` continuation after zero progress,
  short data, EOF, cancellation retirement without socket close, and whole-close
  cleanup. Preserve future write readiness by changing only the read interest bit,
  and rely on existing ByteIoFlow commit/rebuffer semantics when cancellation wins
  after physical bytes were consumed. Add real loopback evidence and release E3C.
  Specification, public Protos API, native boundary, poller cardinality/sharding/
  affinity and license terms are unchanged. Implementation version becomes
  `0.2.313-SNAPSHOT`.

## 0.2.312-SNAPSHOT

- Ratify `PLAT010 — Bounded reusable platform carriers for normal Actor guest execution` and `PLAT011 — RuntimeHost-owned shared Actor carrier substrate across local Processes` after explicit project-owner approval following the PERF001-F #239 fresh-startup failure and exhaustive BEAM/Go/Tokio/Akka/Orleans/Pony/Swift/Kotlin/GHC/OCaml/Java-Loom plus Truffle scalability review. Replace virtual threads as the default normal Actor guest carrier with bounded reusable platform carriers, and place their finite physical capacity at the RuntimeHost/shared-Engine ownership boundary so Actors and local Processes are multiplexed without `O(processes × cores)` platform threads. Preserve one multithread Polyglot Context per hosted Process, concurrent unrelated Actor execution, carrier invisibility, a separate future blocking/offload lane, and future work-stealing/sharding/NUMA/cgroup/resource-governance evolution. Keep PERF001-F #239 open until a bounded implementation repeats the production Actor fan-out at 1/2/4/8 (including repeated width 8) and the full correctness suite. Governance/documentation only: no Protos specification, executable implementation, Maven implementation version, native boundary or license terms change.

- Close `TOOL002-H2A` with H2A2 on top of the published H2A1 asynchronous exact-execution bridge. Add bootstrap-local `executionInspectAsync` as the live-result inspection counterpart, reusing the existing exact inspection request/source naming and `ProtosCapturedProcessExecution.executeThenInspect` mechanism while returning the same ordinary caller-domain Future/completion path as `executionAsync`. Add executable evidence that inspection rematerializes only after caller-domain dispatch, cancellation after captured host completion is first-terminal-wins and discards the queued late result, Actor termination cancels the registered non-task Future, and accepted already-started host submission remains under facility custody until it settles. Preserve the audited native-closure provider inventory by reusing `ProtosExactExecutionFacility.exactExecutionBootstrapClosure`; select no TestPlan scheduler, public jobs/resource policy, JVM carrier, hard timeout/kill, OS-worker or remote backend. Specification and native boundary remain unchanged. Implementation version becomes `0.2.312-SNAPSHOT`.

## 0.2.311-SNAPSHOT

- Close `I026-C — Truffle instrumentability and StandardTags` under ratified PLAT005/PLAT008. Add one common generated `InstrumentableNode` wrapper surface, explicit canonical-role `StatementTag` on direct sequence children and `CallTag` on Call/Send/SuperSend, keep outgoing tool values suppressed until I026-D and reject instrument-injected guest values. Normalize continuation replay to the recursively unwrapped logical guest execution site while still executing the physical wrapper path for real probe events; completed replay entries therefore emit no duplicate execution event. Add focused tag/replay/footprint evidence and retain PLAT004 source ownership plus PLAT001 EXCLUSIVE hosting. No Protos specification or language-semantics change. Implementation version becomes `0.2.311-SNAPSHOT`.


## 0.2.310-SNAPSHOT

- Close `TOOL001-F2E3C` / GitHub #243 and parent `TOOL001-F2E3` / #91. Add the host-mechanical `ProtosExternalPackagePlanningPreflight` boundary: one Package Tool Process receives the confined workspace Filesystem plus temporary views materialized from borrowed F2E2-verified registry/Git custodies, invokes the already-published Protos-owned `ExecutionPlan.buildV2FromVerifiedCaptures`, terminates, and returns only the raw inert generation-2 plan. Incoming custodies remain open on both success and failure for F2E4; no original selected source/store path is reopened or retained and no V2 detach/resolver is introduced. Integration tests delete the original external roots after verification, prove mixed registry/Git planning still succeeds from the same captures, prove failed planning also preserves borrowed custody, and prove no Filesystem reaches the plan. `TOOL001-F2E4` becomes READY. Implementation version becomes `0.2.310-SNAPSHOT`; Core specification, lock format, PackageExecutionPlanV1, public run and license terms are unchanged.

## 0.2.309-SNAPSHOT

- Close `I028-E3A — host-neutral first-effect attempt gate` as the first
  executable consumer of ratified PLAT009. Add transient
  `ATTEMPTING_FIRST_EFFECT` arbitration to `ProtosIoOperation`, preserve the
  existing first-arrival cancellation-versus-close cutover ordering while
  first-effect aftermath is unknown, commit positive first effect before a
  competing zero-effect cutover can publish, and keep host I/O outside lifecycle
  synchronization. Extend the internal ByteWritable backend bridge with an
  optional `FirstEffectWriteCompletion` subtype while leaving existing backends
  source-compatible. Add focused lifecycle and ByteWritable bridge evidence.
  Decompose the remaining E3 work into NIO read, NIO partial-write and integrated
  directional-shutdown/close slices; no TCP byte readiness, production Network
  wiring, poller-count/sharding/affinity or native backend is selected here.
  Specification, public Protos API, native boundary and license terms are
  unchanged. Implementation version becomes `0.2.309-SNAPSHOT`.

## 0.2.308-SNAPSHOT

- Close `TOOL001-F2E3B` / GitHub #236 under ratified D053/D056/D057. Add a bundled-Protos generation-2 constructor that consumes each same-capture F2E2-verified external Filesystem only during preflight, loads exact ManifestV1 from that capture, validates PackageId, ReleaseVersion and active LanguageCompatibilityId, rejects external `path` and `[workspace]`, reconciles registry authority/locator/constraint and Git fetch/revision against the canonical root-owned lock, and emits uniform external dependency edges while keeping Filesystem/custody/path/provenance/host authority out of PackageExecutionPlan. Add mixed workspace->registry->{registry,Git} conformance plus fail-closed identity/version/compatibility, D056/D057, provenance, edge-accounting, descriptor, duplicate-edge and dangling-target evidence. Parent F2E3 remains IN_PROGRESS for final verified-custody composition; F2E4 remains dependency-gated. Implementation version becomes `0.2.308-SNAPSHOT`; Core specification, lock format, public run, host V2 adapter/resolver and license terms are unchanged.

## 0.2.307-SNAPSHOT

- Advance `TOOL002-H2A` with H2A1, the general asynchronous ordinary exact-execution bridge selected by ratified D055. Add a bootstrap-local/test-neutral `executionAsync` facility that accepts an explicitly supplied off-domain host submission mechanism, returns one ordinary caller-domain non-task Future immediately, reuses the existing captured fresh-Process execution path, and enqueues inert host completion back into the caller Actor domain before detached observation rematerialization. Preserve private per-execution output, first-terminal-wins cancellation, pre-start host withdrawal when available, late-result discard, Actor non-task-Future termination participation, and facility custody until accepted host work has finished and its caller completion has been enqueued. Select no concrete JVM Executor/thread/carrier, TestPlan scheduler, jobs/resource/reporting policy, hard timeout/kill, OS-worker or remote backend. H2A remains in progress for H2A2 inspection/cancellation reconciliation. Specification and native boundary are unchanged. Implementation version becomes `0.2.307-SNAPSHOT`.

## 0.2.306-SNAPSHOT

- Ratify `PLAT009 — Host-neutral first-effect attempt gate for asynchronous ByteWritable output` after explicit project-owner approval following the I028-E3 partial-NIO-write race analysis and expanded mainstream-runtime, Truffle/GraalVM and Apple Pkl future-scalability review. Preserve the existing normative first-effect commitment rule with one transient host-neutral attempt state: cancellation/lifecycle cutover may be recorded while first-effect aftermath is unknown, zero-effect attempts return to pre-commit arbitration, and the first positive irreversible contribution commits before a competing zero-effect cutover can publish. Keep host I/O outside lifecycle synchronization, expose no thread/selector/native-request identity, preserve future NIO/io_uring/IOCP/libuv/FFM/WASI/brokered backends, and release I028-E3 to bounded implementation. Reallocate the provisional PLAT008/#238 coordination identifier to PLAT009 because concurrent I026 work durably occupied PLAT008 first. Governance/documentation only: no specification, executable implementation, Maven implementation-version, public Protos API, native-boundary or license-term change.

- Close `LM008-B — Grammar/evaluation/binding/callable surface audit` after B1-B4 final reconciliation. Retain ordinary-Protos evidence for lexical/literal/separator grammar, binding/writes/lowering/evaluation and object composition, Closure invocation/parameters/default/rest/spread/trailing Closures, receiver-bound extraction/`super` and non-local return; add the final first-class-`super` rejection fixtures and missing-`methodHome` argument-vector ordering probe. No new semantic/architectural decision or production implementation gap was found. Parent LM008 remains IN_PROGRESS for C/D/E/F; specification, production implementation, public API, native boundary and implementation version are unchanged.

- Ratify `D057` / GitHub #237 after explicit project-owner approval and expanded cross-ecosystem review: under the current package model, `[workspace]` is mutable resolution-root/development semantics and its presence (including empty `members`) fails closed when ManifestV1 is consumed directly as an immutable registry or exact-Git package. Preserve future explicit immutable source-container + package-root/subroot selection and future per-package publication projection without introducing either implicitly through `workspace.members`. Update ManifestV1 package-model guidance and release `TOOL001-F2E3B` to continue mechanically under D053/D056/D057. Documentation/package-model decision only: no Core specification revision, executable source, implementation version, lock format, PackageExecutionPlan generation, public run behavior or host resolver change.

- Close `I028-E2 — NIO endpoint bridge + non-blocking connectTcp acquisition`
  under D047/D052 and ratified PLAT003/PLAT006/PLAT007. Add the internal numeric
  endpoint-to-NIO bridge, Network-owned IPv6 scope hook, non-blocking
  `SocketChannel` immediate/`OP_CONNECT` acquisition, recognized logical local
  endpoint materialization and C4 commit/cancel/late-custody handoff. Keep zero
  post-connect readiness interest for E3 and physical close/custody only; defer
  byte duplex/half-close, listener/PLAT007, production Network wiring,
  poller-count/sharding/affinity and native backend work. Specification, public
  Protos API, native boundary and license terms are unchanged. Implementation
  version becomes `0.2.306-SNAPSHOT`.

## 0.2.305-SNAPSHOT

- Ratify `PLAT008 — Truffle replay-site identity across wrappers, rewrites and continuations` after explicit project-owner approval and focused cross-runtime review covering Truffle wrapper semantics, Apple Pkl, TruffleRuby, GraalJS generators, FastR, Sulong, Espresso continuations/JDWP, and GraalPy/Bytecode DSL. Select logical replay-site identity with a zero-allocation delegate-backed representation for the current AST and a representation-independent backend contract: wrappers/probes are transparent to replay identity, completed replay does not re-execute instrumentation, SourceSpan/SourceSection are not identity, no per-node token/registry is added now, live replacements must preserve/remap the logical site, and a future Bytecode DSL may use BytecodeLocation or equivalent. Release `I026-C` from its PLAT008 blocker without changing Protos semantics, executable implementation, Maven implementation version, ContextPolicy or license terms. Documentation/governance only.

- Ratify `D056` / GitHub #233 after explicit project-owner approval: `path` dependencies are mutable workspace/local-development relations and are not operationally valid when a package is consumed as an immutable registry or exact-Git instance. Preserve future explicit root-owned override/vendor/patch and immutable multi-package source-model design space without adding either now. Update ManifestV1 package-model guidance and release the remaining `TOOL001-F2E3` work to continue under D053/D056. Documentation/package-model decision only: no Core specification revision, executable source, implementation version, lock format, PackageExecutionPlan generation, public run behavior or host resolver change.

- Close `I028-E1 — bounded JDK-NIO poller primitive` under ratified PLAT006/PLAT007. Add one internal Selector-owning daemon platform-thread poller with concurrent host-control submission, explicit `Selector.wakeup()`, readiness dispatch, poller-thread-only channel registration/interest mutation, per-command/per-channel failure isolation, idempotent shutdown and exact release of registered channels. Retain all NIO readiness/thread/channel identity below the host boundary: E1 adds no Network/TCP operation, no Protos-visible event loop/thread, no poller-count or sharding policy, and no native transport. Add focused real-Selector readiness, concurrent submission, failure-containment and cleanup evidence. Implementation version becomes `0.2.305-SNAPSHOT`; specification, public API, endpoint identity, native boundary and license terms remain unchanged.

## 0.2.304-SNAPSHOT

- Ratify `PLAT007 — JVM NIO IPv6-only TCP listener enforcement` after explicit project-owner approval following mainstream-runtime and Truffle/GraalVM review including Apple Pkl plus future-backend/scalability analysis. Preserve D047 IPv6-only semantics on the public-JDK baseline by representing an unconstrained IPv6 logical listener as an all-or-nothing set of concrete authorized IPv6 `ServerSocketChannel` bindings sharing one port and multiplexed through PLAT006, never by trusting a dual-stack wildcard or filtering IPv4 after accept. Keep scope inside Network authority, preserve explicit-address vs canonical-null distinction, and retain a future native O(1)-socket `IPV6_V6ONLY`/FFM/epoll/kqueue/io_uring/IOCP/WASI/brokered path behind the same host-neutral operation boundary. Release I028-E from the IPv6 platform blocker. Documentation/governance only: no specification, executable implementation, Maven implementation-version, native-boundary or license-term change.

- Ratify `PLAT005 — Truffle instrumentation coverage and StandardTags architecture` after explicit project-owner approval following current Truffle and cross-runtime review including SimpleLanguage, TruffleRuby, GraalJS, Apple Pkl, GraalPy/Bytecode DSL, Sulong and Espresso. Select layered semantic-minimum instrumentation: one replay-aware common `ProtosExpressionNode` wrapper mechanism, immutable canonical-role metadata, `StatementTag` only on direct executable `CanonicalSequence` children, and `CallTag` on Call/Send/SuperSend; keep physical helper roots untagged as semantic roots and defer Root/RootBody/Expression/variable/custom tags, Truffle yield/resume mapping and debugger-value exposure pending their own evidence. Release `I026-C` from its PLAT005 blocker without changing Protos semantics, executable implementation, Maven implementation version, ContextPolicy or license terms. Documentation/governance only.

- Start `TOOL001-F2E3` under ratified D053 with a separate generation-2 bundled-Protos execution-plan constructor for already-verified immutable external leaf packages. Preserve the existing generation-1 workspace-only `ExecutionPlan.build` path unchanged; emit one mixed inert graph with typed workspace/registry/Git refs, one locked ContentIdentity per external node, validated exports and uniform workspace-declared dependency edges. Reconcile registry authority/locator/version-constraint and Git fetch/revision against the canonical lock while excluding that provenance and all Filesystem/custody/path/host authority from the plan. Fail closed on missing/duplicate/content-mismatched descriptors and, for this bounded first slice, on dependencies declared by external nodes pending same-capture external-manifest validation inside the remaining F2E3 work. Implementation version becomes `0.2.304-SNAPSHOT`; specification, lock format, public run and host resolver remain unchanged.

## 0.2.303-SNAPSHOT

- Ratify `D055 — Asynchronous exact-execution boundary for Test Tool outer parallelism` after explicit project-owner approval following cross-runtime, VM and test-runner future-scalability review. Select a general bootstrap-local/test-neutral one-exact-execution asynchronous boundary whose result is an ordinary caller-domain Future; keep max-in-flight scheduling, later capacity/resource admission, CaseId/TestPlan, aggregation and deterministic reporting in bundled Protos; marshal inert captured completion back to the caller Actor before guest rematerialization; retain private per-execution output and fresh Process/Context isolation; do not claim hard preemption for already-started same-runtime work; keep outstanding-execution custody until cleanup; and allow future hardened local, OS-worker or remote backends to implement the same logical contract. Deliberately leave JVM carrier choice, public jobs spelling/default, jobs=auto, hard timeout/kill, resource syntax, retry and remote transport deferred. Documentation/governance only: no specification, executable implementation, Maven implementation-version, native-boundary or license-term change.

- Ratify `PLAT006 — JVM TCP host I/O operation engine architecture` after explicit project-owner approval and expanded cross-language/runtime review. Select a Protos-owned host I/O operation boundary independent of readiness-vs-completion machinery, with bounded non-blocking JDK NIO (`SocketChannel` / `ServerSocketChannel` + `Selector`) as the initial production JVM backend; keep backend threads outside guest Truffle Context execution, preserve existing Future/commit/cancel/late-custody, independent accept, full-duplex and Actor/P confinement contracts, and leave poller cardinality/sharding/affinity plus epoll/kqueue/io_uring/IOCP/Netty backends deliberately deferred. Reclassify the earlier D054/#226 gate as PLAT006/#230 under current decision-family policy and release I028-E to bounded implementation decomposition. Documentation/governance only: no Protos specification, executable implementation, Maven implementation-version, native-boundary or license-term change.

- Close `I026-B — SourceSpan to Truffle SourceSection mapping` as the executable consumer of ratified PLAT004. Keep exact Truffle `Source` identity owned only by each `ProtosRootNode`; derive root and adopted execution-node `SourceSection` values on demand from the existing half-open `SourceSpan`; return no fabricated location for unadopted/source-less nodes; and fail closed when a retained span exceeds the owning Source instead of clipping or remapping it. Add focused identity/range/adoption/failure/footprint evidence and release I026-C to READY. No Protos specification, instrumentation-tag policy, ContextPolicy or license-term change. Implementation version becomes `0.2.303-SNAPSHOT`.

## 0.2.302-SNAPSHOT

- Advance `AUD003-B1` with mechanical tranche `B1b1`: migrate six reviewed ordinary conformance files whose trailing `.and() { ... }` Closures form pure single-expression chains to idiomatic `&&`. Each rewrite preserves left-to-right evaluation, short-circuit laziness, expression result and test purpose, and does not touch Boolean protocol/lowering tests or multi-expression Closure bodies. B1 remains in progress for additional trailing-Closure tranches. Test/source-style only: no implementation-version, specification, public-API, native-boundary or license-term change.

- Ratify `PLAT004 — Truffle SourceSection ownership and materialization` after explicit project-owner approval following current Truffle guidance and cross-implementation review including SimpleLanguage, TruffleRuby, GraalJS, FastR/Sulong and Apple Pkl. Select root-owned exact Truffle `Source` identity plus node-local compact half-open source ranges with `SourceSection` projected on demand after node adoption; reject eager per-node sections and duplicated per-node Source ownership as the baseline, while allowing a future measured non-authoritative derived cache. Release `I026-B` from its platform-decision blocker without selecting instrumentation tags, debugger scope policy, DAP/LSP capability claims, `REUSE`/`SHARED`, or any Protos-visible semantics. Documentation/governance only: no specification, executable implementation, Maven implementation-version or license-term change.

- Correct `GITHUB001` formal-identifier representation policy after the `GITHUB001-H` reconciliation: every formal Protos identifier now has a GitHub Issue representation and stable family label, including `Dxxx`, `PLATxxx`, and `Bxxx`, while normative/specification, platform-decision, and blocker authority remains in the repository. Reconcile the historical inventory as D001-D053, PLAT001-PLAT003, and B001-B010; treat retrospective GitHub timestamps as migration timestamps, and keep the `Protos Development` Project focused on actionable live scheduling rather than requiring closed historical decision/blocker imports solely for inventory. Governance/documentation only: no Protos semantics, runtime implementation, implementation version, native-boundary, lock-format, or license-term change.

- Close `TOOL002-G — Actor/Group scheduler-sensitive corpus migration` with G4 ownership retirement. Remove the two legacy Java/JUnit Actor and Group corpus-policy owners after G2/G3 established complete bundled-Protos Runner ownership over the retained manifests through production Actor scheduling. Add a test-only architecture guard that keeps those duplicate owners retired while requiring the retained G2/G3 full-corpus evidence, Actor/Group Test Tool wiring, and `future-integer-one-of` Runner ownership to remain. Record G1/G1A/G2/G3/G4 durable closure and release TOOL002-H. No production/runtime behavior, Protos semantics, normative specification, native boundary, manifest content or implementation version changes.

- Advance `AUD003-B1 — lazy Boolean spelling audit` with the B1a explicit-Closure tranche. Migrate the seven reviewed ordinary conformance sources that spelled the already-specified lazy Boolean lowering directly as `and(() => ...)` / `or(() => ...)` to idiomatic `&&` / `||`, preserving receiver evaluation, short-circuit laziness, RHS evaluation count, expression result and test purpose. Retain direct Boolean-protocol conformance under `protos/tests/conformance/boolean/**` unchanged as deliberate canonical evidence. B1 remains in progress for the separately classified trailing-Closure `.and() { ... }` population. Test/source-style only: no implementation-version, specification, public-API, native-boundary or license-term change.

- Advance `TOOL002-G3 — retained Group corpus migration` by moving the complete retained `group/manifest.tsv` corpus onto the bundled Protos Test Tool ownership path. Extend the generic Future-resolved expectation machinery with `future-integer-one-of` as value-membership validation only: validate the comma-separated Integer alternatives, await the retained Future through G1/G1A cooperative inspection, and accept any declared value without selecting, observing or constraining the routing member, Group-wide order or scheduler policy. Provision a separate read-only Group corpus capability and exact retained `workers` overlay through the existing G2 mechanism. Keep the legacy Java Group corpus owner temporarily as redundant migration evidence until G4. No test-only scheduler, public language semantics, normative specification or native-boundary change. Implementation version becomes `0.2.302-SNAPSHOT`.

## 0.2.301-SNAPSHOT

- Close `AUD003-A4 — benchmarks and remaining ordinary-program indexing audit/migration` by migrating the final reviewed ordinary indexing spellings in two canonical benchmark workloads and one user-facing language-interactions tutorial to bracket reads/assignments. All `atPut` sites are standalone result-ignored mutations; no protocol, lowering, dispatch, bootstrap, or result-sensitive form is rewritten. The execution-time guard rescans all `protos/benchmarks/**`, `protos/examples/**`, and `protos/tutorials/**` Protos source and rejects new unreviewed debt. No implementation-version, specification, public-API, or license-term change.

- Close `I026-A4B3`, `I026-A4B` and `I026-A4` by retiring the final direct production Process-entry path and installing a structural architecture guard. `ProtosPackageContentVerification` now binds its fresh semantic Process to a Process-scoped Polyglot Context and executes its exact verification Source through public parse/root-task execution. The guard requires every production `ProtosStandaloneProcessBootstrap.create(...)` owner to bind a Process Context and rejects direct `new ProtosSourceCompiler().compile(...)` entry in those Process creators, while preserving the compiler behind `ProtosLanguage.parse`, pre-Process Core/bootstrap preparation and deliberately unhosted Java semantic harnesses. Release `I026-B`; retain `ContextPolicy.EXCLUSIVE` with `REUSE`/`SHARED` deferred. No specification, I028/native-boundary or license-term change. Implementation version becomes `0.2.301-SNAPSHOT`.

## 0.2.300-SNAPSHOT

- Close `I028-D5 — integrated TCP listener conformance + D closure` under ratified D047/D052/PLAT003. Retain one integrated path from `Network.listenTcp` through the ordinary accept-enabled TcpListener, concurrent accepted TcpConnections, resource close, Actor/P confinement and explicit cancellation/late-resource custody. Re-run the D1-D4 and native-boundary evidence; release I028-E for backend design/audit without selecting a production backend, reactor/poller/threading model, host socket/channel identity or endpoint `===` rule. Test/conformance and durable closure only: no specification, production runtime, implementation version, native-boundary or legacy-live-ledger change; boundary remains 135 construction sites / 35 providers.

- Advance `TOOL002-G2 — retained Actor corpus migration` by adding a bundled-Protos ownership path for the complete retained `actor/manifest.tsv` corpus. Provision a separate read-only Actor corpus capability and an explicitly selected Actor execution Prelude whose only non-standard module overlay is the exact retained `workers` fixture; execute every Actor case through the existing generic Test Tool Runner and G1 cooperative inspection path, so production Actor scheduling remains the only concurrency model. Keep the legacy Java Actor corpus owner temporarily as redundant migration evidence until the later G4 retirement slice. Add no new expectation family, scheduler policy, public language/module syntax, normative specification or native boundary. Implementation version becomes `0.2.300-SNAPSHOT`.

## 0.2.299-SNAPSHOT

- Materialize the approved `PERF001-F` canonical concurrency corpus as six ordinary Protos sources covering Future round-trip/fan-out, isolated-P round-trip/Array strong scaling, and Actor request/fan-out. Each source exposes an ordinary `run` Closure and remains directly executable; Actor setup/readiness stays outside the reusable `run` body so the forthcoming production-hosted harness can exclude bootstrap from steady request timing. Preserve fixed logical work, deterministic results, public Future/P/Actor protocols and the existing I026 final-reference gate. Benchmark-corpus only: no Protos semantics, production runtime/library implementation, Maven implementation version, test suite, native boundary or license-term change.

- Repair `TOOL002-G1` exact live-result inspection after the retained Actor corpus exposed a legitimate suspended RootActor continuation at the intermediate cooperative-idle boundary. Keep draining currently runnable RootActor work before inspection, but no longer require intermediate idle to imply zero live tasks; the cooperative inspector may await the returned Future while production Actors progress, and the existing post-inspector zero-live-task invariant remains enforced. Add the retained `actor/ip-data-transfer.protos` chained-Future regression. No test-only scheduler, public API, Protos semantics, normative specification or native-boundary change. Implementation version becomes `0.2.299-SNAPSHOT`.

## 0.2.298-SNAPSHOT

- Close `I028-D4 — Network.listenTcp acquisition` under ratified D047/D052/PLAT003. Add the second shared Network acquisition selector and one host-neutral listen flow. Validate and snapshot exactly the request's own `ipVersion`, `address`, and `port` fields before authority exercise; preserve canonical-null address/port acquisition semantics and matching-version recognized IpAddress constraints; reject missing/delegated/extra/invalid request state with `InvalidIOArgument` and no attributable backend effect. Reuse ordinary Future/Actor cancellation, commitment and explicit late/duplicate/unmaterializable listener custody; successful handoff materializes the existing accept-enabled TcpListener family with its acquired non-zero local port, and a fixed requested port must match before transfer. Add no production backend, host wildcard/ephemeral semantic convention, endpoint-identity rule or socket/channel/reactor identity. Native boundary becomes 135 construction sites / 35 providers; release I028-D5. Specification and legacy live ledgers unchanged. Implementation version becomes `0.2.298-SNAPSHOT`.

## 0.2.297-SNAPSHOT

- Advance `I026-A4B3 — production driver cutover + legacy-entry retirement` with the ordinary module + RootActor initial-module public-parse phase. Resolver-loaded modules in hosted Processes retain the normative Actor-local cache-before-execute lifecycle while parsing their exact `ProtosModuleSource` through the owning Process Context; importable RootActor initial modules use the same public parse boundary and existing RootActor task lifecycle. Remove the workspace staging wrapper around canonical initial-module execution while retaining direct compiler fallback only for explicitly unhosted staging consumers pending the final A4B3 direct-entry retirement/architecture guard. Preserve `ModuleKey` identity, cycles, bootstrap authority, `ContextPolicy.EXCLUSIVE`, deferred `REUSE`/`SHARED`, and all Protos semantics. No specification, I028/native-boundary or license-term change. Implementation version becomes `0.2.297-SNAPSHOT`.

## 0.2.296-SNAPSHOT

- Approve and persist the `PERF001-F` concurrency workload/methodology audit: select a Protos-native fixed-cost + scalability model for Future/P/Actor, define six canonical concurrency workload contracts, require topology-audited physical-core CPU sets plus correctness/raw-sample median/MAD/p95 evidence, require the production Process-scoped Polyglot execution path and the exact `toolchain.json` contract of the measured revision, and gate only the final retained reference run on the relevant I026-A4B3 production-entry retirement. Cross-language concurrency analogues remain outside PERF001-F. Documentation/benchmark-contract only: no Protos semantics, runtime implementation, implementation version, native boundary or license-term change.

- Ratify `D053 — PackageExecutionPlan ABI evolution and external-package representation` after explicit project-owner approval following cross-ecosystem, scalability and Protos-design review. Freeze generation 1 semantically as the exact workspace-only ABI and select generation 2 as one mixed inert workspace/registry/Git graph with compact typed refs, one ContentIdentity per immutable external package node, uniform dependency edges and fail-closed unsupported generations; keep locators, mirrors, paths, ArtifactDigest, verified custody, resolver authority and host handles outside the plan. Release TOOL001-F2E3 to implement the ratified contract. Documentation/governance only: no Core specification, executable implementation, Maven implementation-version, lock-format bytes or license-term change.

- Advance `TOOL002-G` with the first scheduler-sensitive Actor/Group execution slice. Run exact-inspection inspectors as real RootActor-local cooperative tasks so they may suspend on retained Actor/Group request Futures while spawned Actors progress only through the production scheduler; preserve the fresh semantic Process, private-stream and detached-observation boundaries. Add focused mechanism evidence using the retained Actor echo and two-member Group fixtures without selecting a Group routing member. No TestPlan/corpus expectation ownership, test-only scheduler, public API, Protos semantics, normative specification or native-boundary change; later `TOOL002-G` slices retain corpus ownership cutover and Java-owner retirement. Implementation version becomes `0.2.296-SNAPSHOT`.

## 0.2.295-SNAPSHOT

- Close `AUD003-A3 — bundled-tool indexing audit/migration` by classifying the execution-time `protos/tools/**` explicit indexing sites and migrating ordinary readability-only `at` plus result-ignored standalone `atPut` calls across 16 Package/Test Tool modules to the already-specified bracket read/assignment syntax. The launcher rejects new unreviewed files, non-two-argument `atPut`, and any embedded/result-sensitive `atPut` rather than changing its semantics. Preserve evaluation order, dispatch, mutation/result behavior, tool APIs and all Package/Test Tool semantics; no deliberate protocol/bootstrap/reflection exception is rewritten, and the bundled-tool rescan is clean for this equivalence family. No specification, public API or license-term change. Implementation version becomes `0.2.295-SNAPSHOT`.

## 0.2.294-SNAPSHOT

- Close `I028-D3 — TcpListener concurrent accept + custody` under ratified D047/D052/PLAT003. Add one shared `accept` selector and one independent I/O operation per pending accept on the D2 listener lifecycle, preserving multiple-pending progress without a semantic FIFO or owner-thread affinity. Reuse ordinary Future/Actor cancellation, listener-close cutover and explicit late/duplicate/unmaterializable resource release; validate accepted logical endpoint descriptors through the existing D048 representation bridge before materializing the existing TcpConnection family. Add no `listenTcp`, production backend, endpoint-identity rule or host socket/channel/reactor identity. Native boundary becomes 134 construction sites / 35 providers; release I028-D4. Specification and legacy live ledgers unchanged. Implementation version becomes `0.2.294-SNAPSHOT`.

## 0.2.293-SNAPSHOT

- Allocate `D053 — PackageExecutionPlan ABI evolution and external-package representation` as `NEEDS_USER_DECISION` after the TOOL001-F2E3 audit exposed that F2D1 had frozen generation 1 as an exact workspace-only ABI. Publish the durable decision record and block F2E3 on D053 before any generation-1 reinterpretation, generation-2 selection, sidecar graph or host-handle design is accepted. Preserve the closed F2E2 same-capture verification/custody boundary and existing package identity constraints. No option is selected by this allocation; no specification, executable implementation, Maven implementation version, lock-format bytes or license terms change.

- Close `AUD003-A2 — Standard Library indexing audit/migration` by classifying the current `protos/lib/**` explicit indexing sites and migrating the ordinary readability-only `at` / result-ignored `atPut` calls in `collections/Array`, `collections/Set`, `collections/IdentitySet`, `crypto/SHA256`, `io/Files`, and `json/JSON` to the already-specified bracket read/assignment syntax. Preserve dispatch, evaluation order, mutation/result semantics and all public APIs; no result-sensitive/direct-protocol/bootstrap exception is rewritten, no specification or license-term change is introduced, and the Standard Library scan is clean for this confirmed equivalence family. Implementation version becomes `0.2.293-SNAPSHOT`.

## 0.2.292-SNAPSHOT

- Refine GitHub-native coordination discipline: make the owning GitHub Issue the compact execution diary for materially significant work outcomes and confirmed publications, require concise evidence/result/state comments after publication, and reserve `CHANGELOG.md` for published durable project/artifact changes rather than failed attempts, validation-only runs, or live coordination transitions. Issue closure alone does not imply a changelog entry, while a material published change may still be logged before its parent Issue closes. Governance/documentation only: no specification, runtime, implementation-version, native-boundary, or license-term change.

- Close `I028-D2 — TcpListener localPort + close lifecycle` under ratified D047/D052/PLAT003. Install two shared native selectors on the hidden frozen TcpListener protocol, retain the acquired non-zero local port as synchronous runtime observation with no backend effect, and reuse `ProtosIoLifecycle` for one Closable resource cutover whose release result is shared by fresh close Futures while remaining orthogonal to structural Object close. Add no `accept`, `listenTcp`, production backend, endpoint-identity rule or host socket/channel/reactor identity. Native boundary becomes 133 construction sites / 35 providers; release I028-D3. Specification and legacy live ledgers unchanged. Implementation version becomes `0.2.292-SNAPSHOT`.

## 0.2.291-SNAPSHOT

- Advance `I026-A4B3 — production driver cutover + legacy-entry retirement` with the bundled-tool + exact/fresh/captured/workspace Process-context phase. Package/Test Tool entries now use Process-scoped public parse/root-task execution; Test Tool exact/inspection execution hands inert `Source` values to fresh child Processes and parses only inside each child's distinct Context while reusing the driver-owned RuntimeHost/Engine; workspace preflight and application likewise use one driver host with separate Process Contexts, with the existing canonical initial-module machinery only entered under the application Context and intentionally left for the next mechanical cutover. Retire the CLI `legacyToolSession`/Session direct compiler path. Preserve fresh-Process isolation, captured-stream/detached-observation behavior, `ContextPolicy.EXCLUSIVE`, deferred `REUSE`/`SHARED`, and all Protos semantics. No specification, native-boundary, I028 or license-term change. Implementation version becomes `0.2.291-SNAPSHOT`.

## 0.2.290-SNAPSHOT

- Close `I028-D1 — ordinary TcpListener resource/prototype + transfer foundation` under ratified D047/D052/PLAT003. Publish one source-owned runtime-only frozen authority-free TcpListener protocol prototype outside public Prelude bindings and one ordinary structurally OPEN `ProtosTcpListenerValue` child with opaque non-slot host state. Preserve ordinary local-slot mutation/shadowing capacity, canonical protocol-parent identity, explicit Actor/P rejection of the live listener and authority-bearing descendants, and transferability of authority-free descendants of the protocol prototype. Add no `accept`, `localPort`, `close`, `listenTcp`, production backend, endpoint-identity rule or native Closure; native boundary remains 131 construction sites / 34 providers. Release I028-D2. Specification and legacy live ledgers unchanged. Implementation version becomes `0.2.290-SNAPSHOT`.

## 0.2.289-SNAPSHOT

- Close `I028-C5 — integrated TCP connection conformance + C closure` without changing the implementation version or production runtime. Retain one cross-slice harness from `Network.connectTcp` through the ordinary TcpConnection object/protocol, endpoint observation, ordinary local-slot/shadowing behavior, receiver-domain confinement, Actor/P rejection and pre-commit cancellation/late-resource custody. Reconcile the unchanged Core native boundary at 131 construction sites across 34 providers, close I028-C and release I028-D; no specification change, production backend or endpoint `===` strengthening is introduced.

- Close `I028-C4 — host-neutral Network.connectTcp acquisition` under ratified D047/D048/D052/PLAT002/PLAT003. Install one shared `connectTcp` selector on the authority-free Network prototype, require an actual represented Network capability and recognized IpEndpoint before authority exercise, and reuse the existing I/O Future commitment/cancellation machinery for independent asynchronous acquisitions. Explicitly release every late, duplicate, cancelled or unmaterializable acquired resource, map backend failures through the portable I/O Error boundary, and materialize successful results as the existing ordinary TcpConnection family with logical endpoint snapshots. Generalize only the internal ProtosIoLifecycle receiver field to `Object` so represented Network authority can reuse that machinery. No concrete production networking backend, socket/channel/reactor/event-loop identity, specification change or endpoint `===` strengthening is introduced. Native boundary becomes 131 construction sites across 34 providers; C5 integrated conformance/closure becomes READY.

## 0.2.288-SNAPSHOT

- Close `I028-C3 — TcpConnection endpoint observations` under ratified D047/D048/D052/PLAT003. Complete the hidden shared TcpConnection protocol with synchronous `localEndpoint` / `remoteEndpoint` selectors backed by recognized logical `IpEndpoint` snapshots, preserving strict live-family receiver/arity validation and performing no backend network work. Endpoint object identity remains deliberately unspecified, `connectTcp` and production backend selection remain outside C3, and the audited Core native boundary becomes 130 construction sites across 33 providers. C4 host-neutral connect acquisition becomes READY.

## 0.2.287-SNAPSHOT

- Advance `I026-A4B3 — production driver cutover + legacy-entry retirement` by publishing the ordinary CLI/REPL Process-scoped Polyglot entry phase. Bind each ordinary CLI/REPL session's fresh semantic Process exactly once to an explicit PLAT001 `ProtosPolyglotProcessContext`; route `-e` and file root entries through Context-owned parse/root-task execution, route persistent REPL units through Context-owned parse/direct-call without repeatedly reattaching the persistent activation to new Tasks, preserve normalized file URI Source identity, and defer language-bound nested Closure-plan and object-body RootNode/CallTarget materialization until first use, and project any source-backed Closure plan prepared before Context entry into the exact entered Process Context before invocation, so executable roots never reuse an incompatible sharing-layer association, while preserving eager direct/source-only staging behavior when no Polyglot Context is entered. Keep bundled tools explicitly on the staged direct path until their nested exact/fresh/captured/workspace execution chain is migrated together. Existing A+ projection logic and multi-Process evidence remain unchanged. This is one mechanical phase inside the existing I026-A4B3 work item; no new durable sub-item is allocated. No specification, ContextPolicy, Protos semantics, native boundary or license terms change. Implementation version becomes `0.2.287-SNAPSHOT`.

## 0.2.286-SNAPSHOT

- Close `I028-C2 — TcpConnection protocol + duplex lifecycle foundation` under ratified D047/D052/PLAT003. Install the five `read`/`write`/`close`/`shutdownRead`/`shutdownWrite` standard selectors once on the hidden frozen TcpConnection protocol parent, enforce actual operational TcpConnection receiver-domain membership before resource effects, and compose two independent existing Byte-I/O lanes over one shared Closable lifecycle so pending reads cannot head-of-line block writes. Reuse established bounded write snapshots, read cancellation/rebuffering, Future commitment and idempotent directional-shutdown/close behavior; keep resource close distinct from structural `Object.close()`. No endpoint selectors, `connectTcp`, concrete socket/channel/event-loop/backend, endpoint identity strengthening or normative specification change. Native boundary becomes 128 construction sites across 33 providers; C3 endpoint observations becomes READY.

## 0.2.285-SNAPSHOT

- Close `I028-C1 — ordinary TcpConnection resource/prototype + transfer foundation` under ratified D052 / specification `0.1.393` and PLAT003. Publish one source-owned runtime-only frozen authority-free TcpConnection protocol prototype outside public Prelude bindings and an ordinary structurally OPEN `ProtosTcpConnectionValue` child with opaque non-slot host state. Preserve ordinary local-slot mutation/shadowing capacity, canonical protocol-parent identity, explicit Actor/P rejection of the live resource and authority-bearing descendants, and transferability of authority-free descendants of the protocol prototype. Add no TCP selector, `connectTcp`, endpoint observation, socket/channel/event-loop/backend choice or native Closure; native boundary remains 123 sites / 32 providers. Release I028-C2. Specification and legacy live ledgers unchanged. Implementation version becomes `0.2.285-SNAPSHOT`.

## 0.2.284-SNAPSHOT

- Ratify `D052 — TCP live-resource object topology` as specification revision `0.1.393` and `PLAT003 — JVM TCP live-resource and duplex-I/O architecture` after explicit project-owner approval and cross-language/runtime/full-duplex/future-scalability review. Fix acquired TcpConnection/TcpListener as ordinary OPEN identity-bearing objects delegating to shared frozen authority-free family protocol prototypes without required public Prelude bindings; keep delegation distinct from TCP family membership/authority and keep resource `close()` distinct from structural Object close. Select JVM ProtosObjectValue-derived resource wrappers with opaque host state, shared protocol installation, existing I/O acquisition commitment/late-custody reuse, and independent read/write progress lanes with shared lifecycle. Preserve D047 endpoint structural equality while deferring stronger endpoint `===` identity, and select no production network backend or generic HostResource hierarchy. Specification/governance only: no production runtime, Maven implementation-version, native-boundary or license-term change; I028-C remains READY.

- Close `I028-B5` and parent `I028-B — Network capability + bootstrap provisioning` by reconciling the retained B1-B4 evidence under D047/D048 and ratified PLAT002. Confirm the authority-free `Network` prototype, represented host authority, optional RootActor-local `network` endowment, no ambient recovery through Prelude/Process/import/non-root Actor, and Actor/P rejection; preserve the 123-site / 32-provider native boundary and release I028-C. Reconciliation/documentation only: no implementation-version, production-runtime, specification, TCP/backend or license-term change.

- Close `I028-B4 — ambient/import/Actor/P confinement conformance` without an implementation-version or production-runtime change. Add retained integrated evidence that the authority-free `Network` prototype and the fixed eight-selector `Process` protocol cannot recover a concrete Network grant, imported modules and hosted non-root Actors cannot resolve RootActor-local `network` ambiently, and the exact B3-provisioned capability is rejected by Actor/P transfer. Preserve B2/B3 focal evidence, the 123-site / 32-provider native boundary, and all D047/D048/PLAT002 semantics; release I028-B5 for B-level reconciliation. No spec, production source, TCP/backend or license-term change.

- Close `I028-B3 — optional RootActor network bootstrap endowment` under ratified D047/PLAT002. Allow one already-provisioned represented Network capability to be retained as bootstrap-stable Process-host state and expose that exact capability only as local `network` on the initial RootActor module / standalone initial activation before source execution. Absence is an absent slot, imported modules and newly hosted Actors receive no ambient grant, and the public Process protocol gains no Network accessor or authority recovery path. Preserve the existing standalone-bootstrap signature while adding an overload for the optional Network capability. Release I028-B4 for retained authority-confinement closure. No specification, connect/listen/TCP/backend, native-Closure inventory or license-term change. Implementation version becomes `0.2.284-SNAPSHOT`.

## 0.2.283-SNAPSHOT

- Close `I028-B2 — represented Network capability + transfer confinement` under ratified PLAT002. Add `ProtosNetworkCapabilityValue` as a dedicated `ProtosRepresentedValue` wrapper whose exact canonical `Network` parent and opaque host authority target stay outside ordinary Protos slots; expose the canonical Network prototype to runtime code through `ProtosPrelude.networkPrototype()`. Make Actor and isolated-P transfer rejection explicit for the live capability and for ordinary descendants whose delegation chain reaches it, while proving an ordinary child of the authority-free Network prototype remains ordinary transferable data. Release I028-B3 for optional RootActor `network` bootstrap provisioning. No RootActor endowment, connect/listen/TCP resource, backend/event-loop selection, native Closure expansion, normative specification or license-term change. Implementation version becomes `0.2.283-SNAPSHOT`.

## 0.2.282-SNAPSHOT

- Start `AUD003 — Protos source-style conformance audit` as the durable owner for repository-wide migration to the already-approved idiomatic-source policy. Adopt historical `SOURCE-STYLE-INDEXING-A1` / `a848371a` as closed `AUD003-A1`, make Standard Library indexing `AUD003-A2` READY, record the confirmed sugar families plus non-equivalence traps (`div`, generic `add`, blanket `mod`), preserve deliberate protocol/lowering/bootstrap forms, and require a later exception-aware prevention gate plus full rescan before closure. Documentation/governance tracking only: no normative specification, executable source, implementation version, native boundary or license-term change.

- Ratify `PLAT002 — Network capability represented-value architecture` after explicit project-owner approval and cross-language/runtime/capability/scalability review, and close `I028-B1`. Select a future concrete `ProtosNetworkCapabilityValue` over the existing implementation-only represented-value bridge, with opaque host authority, ordinary delegation to the standard `Network` prototype, fail-closed Actor/P transfer, no generic HostCapability hierarchy, and no backend/event-loop identity in Protos semantics. B1 itself publishes only the canonical frozen source-backed `Network` Prelude prototype plus exact bootstrap/source/Test Tool inventories and prototype conformance; it intentionally adds no authority instance, RootActor `network` endowment, connect/listen/TCP object, backend selection or native Closure site. B2 becomes READY. No normative specification or license-term change; native boundary remains 123 sites / 32 providers. Implementation version becomes `0.2.282-SNAPSHOT`.

## 0.2.281-SNAPSHOT

- Start `LM008 — Core Language Surface Completeness` and close documentation-only `LM008-A`. Establish a spec-owner -> guest-visible implementation -> executable Protos conformance audit matrix, an evidence hierarchy that does not treat Java primitive tests as proof of guest reachability, and independent B-E audit fronts with final F reconciliation. Seed LM008-C with the currently evidenced `Object.slotNames`, `removeSlot`, structural `close` and structural `freeze` publication gaps; record the now-closed I025 ordering repair as covered baseline, and classify D044-deferred `break`/`continue` as outside current normative Core. Documentation/governance only: no normative specification, production runtime, tests, native-boundary, implementation-version or license-term change.

- Close I025 by enforcing specification `0.1.386` required-before-default Closure parameter ordering in one linear parser pass before executable Closure production. Preserve optional-final-rest and the existing left-to-right runtime binder unchanged; publish focused parser rejection including a Protos-source negative syntax fixture, add executable main-manifest conformance for legal `required* -> default* -> rest?`, and migrate the three legacy `while` no-preflight probes away from their now-invalid declaration order without weakening their activation assertions. Require focused parser/Test Tool/CLI validation, the complete Maven package suite, and the public `protos test` corpus before publication. No normative specification or license-term change; implementation version becomes `0.2.281-SNAPSHOT`.

## 0.2.280-SNAPSHOT

- Close `I028-A3 — Actor/P transfer + A closure` and parent `I028-A` without new production machinery. Add Protos-source Actor and isolated-P round-trip conformance for max-width IPv6/max-port `IpEndpoint` data; both prove canonical `IpAddress`/`IpEndpoint` parents, transparent recognized frozen/exact-slot state, recursively preserved nested address data, fresh destination identities, and structural equality/hash after rematerialization. The implementation reuses the pre-existing generic ordinary-object Actor/P snapshot paths exactly as specified; no networking transfer whitelist/value family, Network/TCP/backend behavior, native Closure site, normative specification, public API/syntax, license term or Maven implementation-version change. `I028-A` is CLOSED; I028 remains IN_PROGRESS for B-F.

- Close `I026-A4B2B3B — A+ context-local executable projection + final concurrency closure`, completing `I026-A4B2B3`, `I026-A4B2B`, and `I026-A4B2`. Mark the globally shared source-backed `Object.init`, `Object.==` and `Object.!=` semantic Closures as requiring entered-Context executable projection while retaining their shared semantic identity and direct-staging template. Each active `ProtosLanguageContext` now owns a bounded concurrent cache keyed by the shared template plan and rebuilds fresh language-bound parameter/body CallTargets for that Context, preserving source provenance; bound-method materialization reuses the same template key rather than growing the cache per receiver. Hosted invocation never mutates the shared Closure plan, introduces no JVM-global Context-to-plan map and takes no global guest lock/GIL, while the existing direct fallback remains until A4B3 retires it. A reentrant host-only entered-scope marker prevents the staged direct path from querying Truffle `ContextReference` outside its documented precondition; the marker carries no Protos/Context identity or semantic state and creates no carrier affinity. Focused evidence drives the same global `Object.!=` behavior concurrently through two distinct Process Contexts on one Engine, proves both executions overlap, proves distinct Context-owned plans and both underlying CallTargets, and proves the shared template remains unchanged. Close B010 and complete D049 implementation; release `I026-A4B3` to READY. No normative specification, public Protos semantics/API/syntax, ContextPolicy, native-boundary inventory, I028 or license-term change. Implementation version becomes `0.2.280-SNAPSHOT`.

## 0.2.279-SNAPSHOT

- Close `I028-A2 — IpEndpoint local foundation` on the already-ratified D047/D048 networking-data contract. Publish source-owned canonical `IpEndpoint` as a frozen ordinary Prelude factory/prototype; ordinary invocation accepts only a recognized standard `IpAddress` plus exact unbounded Integer port `1..65535`, retains the exact supplied address identity, and returns one fresh frozen child with exactly `address` / `port`. Add callback-free transparent `recognizes(value)` and structural `==` / `hash` that compose canonical IpAddress state plus port, including equal-but-distinct nested address and Map-key coherence conformance. Reuse A1's internal direct IpAddress invariant inspection rather than duplicating address membership rules. Re-audit the native boundary from 119/31 to 123/32. I028-A3 becomes READY; no Actor/P transfer, Network/TCP backend, DNS, UDP, TLS, normative specification, public syntax/API or license-term change. Implementation version becomes `0.2.279-SNAPSHOT`.

## 0.2.278-SNAPSHOT

- Close `I028-A1 — IpAddress local foundation`, the first mechanical subdivision of the already-ratified D047/D048 address/endpoint foundation. Publish source-owned canonical `IpAddress` as a frozen ordinary Prelude factory/prototype; ordinary invocation validates exact unbounded Integer `version`/`bits`, enforces IPv4/IPv6 bounds, and returns one fresh frozen ordinary child with exactly those two local slots. Add transparent exact-shape `recognizes(value)` plus structural `==`/`hash` without a Java value family or candidate callbacks, with ordinary-Protos conformance for freshness/freeze, validation, recognition, equality/hash and Map-key coherence. Re-audit the native boundary from 115/30 to 119 construction sites / 31 providers for the bounded representation bridge. Mechanically refine I028-A into A1 CLOSED, A2 READY and A3 dependency-gated. No `IpEndpoint`, Actor/P transfer, Network/TCP backend, DNS, UDP, TLS, normative specification or license-term change. Implementation version becomes `0.2.278-SNAPSHOT`.

## 0.2.277-SNAPSHOT

- Close `I026-A4B2B3A — frozen shared-standard Core publication`, the first mechanical half of D049/A+ B2B3 implementation. Publish the JVM-global standard `Object` through one bounded synchronized construction cutover that installs its complete native/source-backed/hash/Future/parallel surface before freezing every root Closure and the root itself; later concurrent bootstraps only validate/reuse that publication. Seal each bootstrap's physically shared standard graph, including semantic Closure captures, before Prelude exposure without introducing recursive guest `freeze`, a global guest lock, Actor-local Core copies, or a ContextPolicy change. Add ordinary-Protos root/prototype-mutation conformance, migrate all six identified legacy numeric lookup/receiver probes away from shared-prototype mutation, make the Test Tool whole-corpus gate report exact failed CaseIds, and add frozen-graph/capture plus concurrent bootstrap reuse evidence while retaining the native-boundary inventory. B010 remains READY and `I026-A4B2B3B` becomes READY for A+ per-`ProtosLanguageContext` executable projection/final multi-Process closure. No specification, public API/syntax, native provider count or license terms change. Implementation version becomes `0.2.277-SNAPSHOT`.

## 0.2.276-SNAPSHOT

- Adopt repository-wide Protos source-style policy: ordinary hand-written Protos source, including Standard Library, tools, tutorials/examples and representative Protos tests, normally prefers stable specified idiomatic syntactic sugar over systematically exposing its canonical/desugared protocol form. Preserve explicit canonical spelling when implementing or testing the underlying mechanism, crossing a bootstrap/layering boundary, or when it is materially clearer; prohibit unrelated mechanical rewrites and make clear that this policy approves no new syntax or semantics. Add the human-facing style reference and contributor/agent routing. Documentation/governance only: no normative specification, production implementation, tests, Maven implementation-version or license-term change.

- Ratify `D051 — Conditional surface syntax boundary` as specification revision
  `0.1.392`. Keep `if` and `else` ordinary identifiers, preserve
  `if(condition) { ... }` as an ordinary call with a trailing Closure, and
  explicitly reject dedicated/contextual `if`/`else` syntax for Core v0.1 while
  retaining D050's Boolean protocol as the canonical conditional mechanism. Add
  two Protos-source compatibility cases. Specification/test/governance only: no
  production implementation, new `Ixxx`, reserved word, native boundary,
  implementation-version or license-term change.

- Close `I030 — Standard Object structural-view publication`, a runtime implementation finding exposed by LM007-D. Publish the already-normative inherited `Object.without(name)` and `Object.alias(sourceName, aliasName)` messages through the existing ordinary-object representation bridge, reusing the existing shallow `ProtosObjectValue` structural helpers. Add ordinary-Protos coverage for symbolic selector aliasing with receiver binding, exact shallow value preservation, fresh Object-parented results, local-only source selection, and alias conflict failure; update the audited native boundary from 113 to 115 construction sites while keeping 30 providers. No normative specification, grammar, public semantic contract, license term, or LM007 test expectation changes. Implementation version becomes `0.2.276-SNAPSHOT`.

## 0.2.275-SNAPSHOT

- Refresh closed `DOC001-E` Guide 04 after D050 / specification `0.1.390`: document strict Boolean `not()` and unary `!`, ordinary two-way `ifTrueIfFalse(trueBlock, falseBlock)` selection, eager callback-producing argument-expression evaluation with selected-only callback validation/invocation, and representative Protos conformance links. Documentation only: no normative specification, implementation, tests, native boundary, implementation-version or license-term change.

- Ratify `D048 — IpAddress / IpEndpoint construction and recognition` as specification revision `0.1.391` after explicit project-owner approval of the bounded post-D047 checkpoint. Select canonical frozen prelude `IpAddress`/`IpEndpoint` factory-prototypes invoked through ordinary call syntax, fresh frozen ordinary-object results with exact transparent local state, exact immediate canonical parent recognition without hidden branding, and explicit `recognizes(value)` predicates. Preserve D047 numeric address/endpoint laws, ordinary object identity, explicit Network authority and DNS/UDP/TLS exclusions; reconcile I028 from OPEN to READY and release I028-A without implementing networking. Specification/governance only: no production implementation, persisted tests, Maven implementation-version bump, native-Closure boundary or license-term change.


- Close `LM006-F` and parent `LM006 — System & Resource Language Maturity`. Publish executable learning material for mutable Bytes/Encoding, explicit Process output and explicitly provisioned Filesystem text round trips; wire the Filesystem lesson into the existing LM006-E host-capability harness and reconcile A-E through their current Test Tool/dedicated regression owners. Preserve the standalone CLI rule that application code receives no default Filesystem authority. Test/learning/governance only: no production Java/runtime, normative specification, public API, native boundary, license term or implementation-version change.

- Ratify `D050 — Standard Boolean protocol completion` and close `I029` atomically. Add canonical `not()` and `ifTrueIfFalse(trueBlock, falseBlock)` to the ordinary strict-Boolean protocol, confirm existing `!value -> value.not()` lowering, preserve ordinary eager argument-expression evaluation with selected-only callback validation/invocation, and return the selected branch's exact normal result. Add Protos-source conformance for direct/operator negation, canonical branch selection, exact-once invocation, eager argument-expression effects, unselected non-invokable tolerance, selected non-invokable failure, and non-Boolean receiver rejection. Keep `if`/`else` sugar, truthiness, ternary, `unless`, `xor`, inverse branch selectors, Boolean prototypes and new reserved words out of D050. Native construction-site totals remain 113 across 30 providers. Specification revision becomes `0.1.390`; implementation version becomes `0.2.275-SNAPSHOT`.

## 0.2.274-SNAPSHOT

- Ratify `D049 — shared standard-object publication and root immutability` as specification revision `0.1.389` after explicit project-owner approval of the B010 decision packet. Publish the unique standard root `Object` frozen from first guest-observable access and require every physically shared standard object with observable structural state to satisfy the same object-by-object semantic-immutability boundary; retain shallow freeze, Actor-local mutable semantic state, child-prototype/composition/local-shadowing mechanisms, and the separately ratified A+ Truffle executable-layer split. Resolve B010 normatively and move B010/I026-A4B2B3 to READY without implementing B2B3, changing `ContextPolicy`, touching I028, or bumping the Maven implementation version. Specification/governance only: no production implementation, persisted tests, native-Closure boundary or license-term change.

- Close `LM006-E — end-to-end system/resource interaction maturity`. Add six ordinary-Protos integration programs over one host-only deterministic harness: Process bootstrap data -> Array reduction -> whole-file text round trip -> Process output; stdin -> file -> stdout; concurrent whole-file Futures joined with `Future.all` and Array reduction; EncodingError recovery through Process environment data and a fallback file; whole-file mutable Bytes copy/mutation; and direct File + borrowing TextReader + `ensure` + Process output. Close E and release LM006-F to READY. Test/conformance/governance only: no production Java/runtime, normative specification, public API, native boundary, license term or implementation-version change.

- Ratify `D047 — explicit capability-oriented TCP networking foundation` as specification revision `0.1.388` after explicit project-owner approval and cross-OS/language/capability/future-scalability review. Add normative `spec/io/NETWORK.md`, reconcile I/O/Process/Actor/P ownership, and allocate `I028` as the `OPEN` Core implementation owner while `LIB005` becomes dependency-blocked. Keep exact public `IpAddress`/`IpEndpoint` construction selector/recognition spelling for a later bounded approval checkpoint rather than inventing it in this ratification. Specification/governance only: no production implementation, persisted tests, Maven implementation-version bump, native Closure boundary or license-term change.

- Close `I027 — Unqualified receiver-fallback Closure binding alignment`. Fix the already-normative bare-name receiver phase so it performs the same ordinary member-read materialization as explicit member access: after lexical exhaustion, a selected Closure is rebound to the original dynamic receiver and the exact selected slot home, while lexical-slot Closure reads remain exact and unbound. Centralize that rule in `ProtosValueLookup.readMember(...)`, consume it from `ProtosActivation.lookup(...)` and `ProtosMemberReadNode`, and retain one ordinary-Protos regression plus a focused runtime mechanism guard. This fixes the object-model maturity reproducer in which nested `read()`/`evaluate(...)` calls incorrectly ran with the prototype-construction receiver. No normative specification, public Protos API/syntax, native selector inventory or license-term change. Implementation version becomes `0.2.274-SNAPSHOT`.

## 0.2.273-SNAPSHOT

- Close `LM006-D — Resource lifetime, Error and ensure maturity`. Add five Protos-source interaction cases over one host-only deterministic File backend: exact normal-result preservation through owned `File.close().value()` cleanup, exact body-Error preservation after successful close, later close `IOError` superseding an already-selected body Error, borrowing `TextReader` failure with outer File ownership, and a Future that records and repeatedly re-signals the later cleanup Error rather than the earlier EncodingError. Reuse I022/LIB004-A cancellation-race evidence instead of duplicating it. Test/conformance/governance only: no production Java/runtime, normative specification, public API, native boundary, license term or implementation-version change.

- Close `LM006-C — Process and standard-stream interaction maturity`. Add five Protos-source cases on the existing host-neutral Process conformance harness: root/delegated Process capabilities consume one shared stdin sequence, root/delegated `std:io/ProcessStreams` writers target the same stdout binding, raw byte output composes with explicit text output, unavailable stdin does not poison independently available stdout, and stable Process args/environment feed text output through the Process-selected Encoding. Keep LIB004-D's already-closed freshness/borrowing checks as component evidence and leave Process termination/cleanup precedence to I017/LM006-D. Test/conformance/governance only: no ambient Process, default Encoding, production Java/runtime, normative specification, public API, native boundary, license term or implementation-version change.

- Close `LM006-B — File/Filesystem/Path interaction maturity`. Add five ordinary-Protos capability-level cases with one host-only deterministic in-memory Filesystem backend: Path-driven default open with read/seek/position/size, two independent File cursors over one shared selected resource, positioned write beyond EOF with portable zero-valued gap and cross-open visibility, truncate with preserved logical cursor and shared-content visibility, and `seekToEnd()` followed by ordinary positioned write. Keep I021 namespace mutation, I024 captured-tree behavior, LM006-D cleanup/Error precedence, Process concerns and unfinished I026 outside this slice. Test/conformance/governance only: no production Java/runtime, normative specification, public API, native boundary, license term or implementation-version change.

- Close `I026-A4B2B2 — P carrier Process-Context routing`, the second mechanical subdivision of the already-approved A4B2B integration. Refine the existing runtime-only P-domain membership registry into P-domain placement metadata so isolated guest execution can enter the originating Process's fixed `ProtosProcessExecutionHost` without transferring or exposing Process/Context authority. Snapshot submission derives placement from the owning Actor Process and nested P inherits it from its parent P domain; standalone/unbound migration paths remain direct. Keep semantic snapshotting synchronous, defer fresh Closure CallTarget rematerialization until first invocation inside the Process Context so staged direct-entry ASTs are never executed in another Truffle sharing layer, and wrap only guest invocation in the Process host rather than imposing Context entry on inert queue/snapshot/result bookkeeping. Attach each fresh P module activation to its exact root `ProtosTask` before guest invocation so nested `Future.value()` uses ordinary cooperative suspension/replay and nested P producer work remains structurally owned by the current P task. Focused Polyglot evidence proves two sibling P computations overlap on distinct carriers while observing the same exact Process `ProtosLanguageContext`, and proves a fresh nested P domain inherits that same placement while preserving ordinary Future/P result semantics. Add no Context-per-P mapping, carrier affinity, semantic ThreadLocal, global execution lock/GIL, P-visible Process authority or Core-bootstrap synchronization. A4B2B3 becomes READY for concurrent Core-root bootstrap closure; A4B2/A4B3 remain open and `ContextPolicy.SHARED` remains deferred. No normative specification, P/Process/Actor/Future semantics, public CLI, native-boundary construction count or license terms change. Implementation version becomes `0.2.273-SNAPSHOT`.

## 0.2.272-SNAPSHOT

- Start `LM006 — System & Resource Language Maturity` and close `LM006-A — Bytes/Encoding composition maturity`. Add five ordinary-Protos main-manifest cases that compose already-closed mutable Bytes and mandatory portable Encoding semantics: manual UTF-8 construction, decode after byte mutation, independent fresh encode results under mutation, manual UTF-16BE construction, and strict malformed UTF-8 after mutation. Persist LM006 with B/C/D/E independently READY and F dependency-gated; keep unfinished I026 outside the maturity dependency surface. Test/conformance/governance only: no production Java/runtime, normative specification, public API, native-boundary, license-term or implementation-version change.

- Close `LIB004-E` and the bounded parent `LIB004` after final cross-slice closure validation. Rerun the existing Protos-source A/B/C/D conformance through the filesystem-library and Process integrated harnesses together, rerun the Core native-boundary architecture guard, and rerun the complete Maven suite on the exact publication base before publication. Reconcile the canonical project ledger and record the final architecture closure without adding another API, distributable/library source, Java source/test, Protos test, specification change, native standard operation, or implementation-version bump; the implementation version remains `0.2.272-SNAPSHOT`.

- Close `LIB004-D — ProcessStreams`. Publish `std:io/ProcessStreams` in ordinary Protos with exactly `stdinReader(process)`, `stdoutWriter(process)` and `stderrWriter(process)`, each a direct fresh borrowing `TextReader`/`TextWriter` construction over the explicitly supplied Process standard byte stream and its Process-provided Encoding. No wrapper cache, ambient/current Process, default UTF-8, stream ownership transfer or Java/native operation is added. Four Protos-source conformance cases prove fresh wrappers, distinct Process-selected encodings, borrowing close behavior and explicit Process authority; the existing Process integrated Java harness only provisions byte backends and runs those cases. LIB004-D closes and LIB004-E becomes READY. No specification or production-Java/native-boundary change; implementation version becomes `0.2.272-SNAPSHOT`.

## 0.2.271-SNAPSHOT

- Close `I026-A4B2B1 — Actor carrier Process-Context routing`, the first mechanical subdivision of the already-approved A4B2B integration work. Keep `ProtosActorScheduler` Truffle-neutral and route exactly each selected non-preemptive Actor segment through the Actor's owning Process `ProtosProcessExecutionHost`; initialization/control turns, runnable Task segments and accepted mailbox turns therefore enter the fixed Process Context when hosted, while Actors without a Process and the staged unbound-Process path remain direct until A4B3. Focused runtime evidence proves all three scheduler segment classes cross the host boundary, and focused Polyglot evidence runs two distinct Actors concurrently on two carrier threads and proves both observe one exact `ProtosLanguageContext` for their Process. Introduce no Context-per-Actor mapping, carrier affinity, semantic ThreadLocal, global execution lock/GIL, P routing or Core-bootstrap synchronization. Refine remaining A4B2B mechanically into B2B2 P carrier routing (READY) and B2B3 concurrent Core-root bootstrap closure (dependency-blocked); A4B2/A4B3 remain open. No normative specification, Process/Actor/Task/mailbox/P semantics, public CLI, native boundary or license terms change. Implementation version becomes `0.2.271-SNAPSHOT`.

## 0.2.270-SNAPSHOT

- Close `LIB004-C — whole-file text helpers`. Publish ordinary-Protos `std:io/Files.readAllText(filesystem, path, encoding) -> Future<String>` and `writeAllText(filesystem, path, text, encoding) -> Future<Filesystem>` by composing the closed A/B whole-byte helpers with the supplied Core Encoding's synchronous one-shot codec. `readAllText` decodes only after `readAllBytes` has completed its owned File cleanup; `writeAllText` encodes synchronously before `writeAllBytes` can perform any Filesystem effect, so EncodingError cannot truncate or partially rewrite the target. Keep Encoding explicit with no ambient/default selection and add nine Protos-source cases covering Latin1, strict UTF8 decode failure, UTF16BE, missing Encoding, pre-I/O EncodingError, cancellation and read/write close-failure composition while reusing the single filesystem-library Java harness. LIB004-C closes; LIB004-D is preserved and LIB004-E becomes READY iff D is already CLOSED. No specification or production-Java/native-boundary change; implementation version becomes `0.2.270-SNAPSHOT`.

## 0.2.269-SNAPSHOT

- Close `I026-A4B2A — shared Engine + Process Context lifecycle`, a mechanical subdivision of the already-ratified PLAT001 A4B2 implementation. Add one explicit non-singleton `ProtosPolyglotRuntimeHost` that owns a shareable Truffle Engine and creates distinct multithread Contexts for independently hosted semantic Processes; bind each Process exactly once through a host-neutral internal `ProtosProcessExecutionHost` rather than exposing Graal classes or promoting Context identity into Process semantics. Extend the A4B1 lifecycle bridge so semantic Process termination requests physical Context close only after the Process is already terminal, defers close safely when termination completes on an entered carrier, rejects later entry, and leaves sibling Process Contexts on the same Engine usable. The Engine owner refuses to close while live Process Contexts remain instead of implicitly terminating/cancelling Processes. Split remaining A4B2 mechanically into A4B2A/A4B2B; A4B2B becomes READY for Actor/P carrier routing and bounded concurrent Core-root bootstrap publication, while A4B3 remains dependency-blocked. Keep `ContextPolicy.SHARED`, exact Engine topology, pooling and Native Image policy deferred. No normative specification, public CLI, Process/Actor/Task/P semantics, native boundary or license terms change. Implementation version becomes `0.2.269-SNAPSHOT`.

## 0.2.268-SNAPSHOT

- Close `LIB004-B — writeAllBytes`. Publish `std:io/Files.writeAllBytes(filesystem, path, bytes) -> Future<Filesystem>` in ordinary Protos. Capture one private invocation-time Bytes snapshot synchronously before any Filesystem effect through the exact Latin1 byte-preserving one-shot Core conversion, then use one explicit write/create/truncate/positioned open and sequential at-most-65536-byte writes, never retrying a failed ordinary write whose Core contract may already have committed a contiguous prefix. Reuse LIB004-A strong pending-open and owned-File custody and D043 ensure cleanup so success resolves to the exact supplied Filesystem only after close succeeds, while open/write/close failure and cancellation preserve the already-normative partial-effect aftermath with no rollback promise. Add nine Protos-source conformance cases for snapshot mutation isolation, empty truncate, bounded multi-chunk output, committed-prefix failure, close failure, two cancellation boundaries, pre-I/O invalid input and open failure; reuse the single filesystem-library Java harness only to provision/control backend races. LIB004-B closes and LIB004-C becomes READY. No specification or production-Java/native-boundary change; implementation version becomes `0.2.268-SNAPSHOT`.

## 0.2.267-SNAPSHOT


- Close `I026-A4B1 — Truffle multithread safety + carrier entry`. Implement ratified PLAT001's first executable safety gate on GraalVM/Truffle 25.3.4.1: audit the language/context/compiler state, make `ProtosLanguage` explicitly allow concurrent external thread access, and replace A4A's permanently-entered owner-thread Context with bounded enter/leave on each calling carrier. Coordinate lifecycle only with one fair per-Context read/write lock whose read side permits overlapping guest execution and whose write side waits for in-flight executions before close; add no global execution lock/GIL and no semantic ThreadLocal. Focused evidence forces two distinct carrier threads to overlap inside one Context while observing one exact `ProtosLanguageContext`, verifies close cannot race an entered execution and rejects later entry, and retains registration/Source parsing plus Actor/P concurrency tests. Record the existing static mutable Core-root bootstrap publication as an explicit A4B2 requirement rather than hiding it behind a global bootstrap lock; A4B2 becomes READY for shared-Engine/Process-scoped hosting and Actor/P binding, while `ContextPolicy.SHARED` remains deferred. No normative specification, public CLI, Process/Actor/Task/P semantics, native boundary or license terms change. Implementation version becomes `0.2.267-SNAPSHOT`.


- Create the `PLATxxx` project family for durable non-normative platform/runtime architecture decisions, explicitly separating them from normative `Dxxx` language/specification decisions and from their `Ixxx`/CLI/tool/performance/distribution consumers. Ratify `PLAT001 — Truffle runtime hosting topology` by explicit project-owner approval on 2026-09-08 after comparison with Espresso, TruffleRuby, Sulong, GraalWasm, GraalPy and JavaScript-style isolation plus scalability/future review. Select a shareable Truffle Engine with one current multithread Context per hosted Protos Process, while keeping Engine != Node, Context != Process and Thread != Actor; reject Context-per-Actor/carrier and global execution serialization, require an implementation thread-safety gate before `isThreadAccessAllowed` is relied upon, and leave `ContextPolicy.SHARED`, pooling/preinitialization, exact Engine topology and Native Image policy deferred. Refine I026-A4B into A4B1 multithread safety, A4B2 Process-scoped hosting, and A4B3 production-driver cutover. Governance/documentation only: no normative specification, production implementation, implementation version, native boundary or license-term change.

- Close `LIB004-A3 — pending-open owned-acquisition custody closure`. Add one Protos-source acquisition-race case to the existing single filesystem-library Java harness: cancel `readAllBytes` only after its one default read-existing `Filesystem.open` is pending, require the helper to request cancellation of the retained open Future and reach terminal cancellation without acquiring/using a File, then deliver a late successful backend resource and prove the standard open producer releases that untransferred custody exactly once with no File read/close. Re-audit the A1 retained-open-Future/`ensure` pattern together with A2 post-acquisition evidence and the unchanged native-boundary guard. `LIB004-A3` and parent `LIB004-A` close; `LIB004-B` becomes READY. Test/conformance only: no `Files.protos`, production Java, specification, implementation-version, native-boundary or license-term change.

- Close `LIB004-A2 — readAllBytes adversarial owned-File lifecycle conformance`. Keep the A1 `std:io/Files.readAllBytes` implementation and public surface unchanged while adding Protos-visible cancellation/Error observations over a host-controlled standard Filesystem/File backend. The retained evidence forces cancellation only after successful File acquisition and one accepted pending read, proves queued/pending reads are cut over before owned File release, proves the helper Future remains pending while `file.close().value()` cleanup itself is suspended, proves read failure propagates only after successful close, proves close failure replaces normal completion, and composes an earlier read failure with a later close failure under the already-normative D043 `ensure` precedence. `LIB004-A2` closes and `LIB004-A3` becomes READY. Test/conformance only: no `Files.protos`, production Java, specification, implementation-version, native-boundary or license-term change.


- Reconcile canonical project-status ledger drift without changing any real work-item lifecycle. Align AUD002/I026, I024/B009/TOOL001, I023/DOC001, TOOL001-F2, PERF003 and DIST001 current-state summaries with their already-published owner records; regenerate the B-family registry entries through B009 so B007-B009 are CLOSED; preserve historical transition wording inside closed slice descriptions where it remains historical evidence. Documentation/governance only: no specification, implementation, blocker-owner, implementation-version, runtime, native-boundary or license-term change.

- Publish `I026-A4A — entered Polyglot execution context`. Refine the already-approved A4 cutover into bounded A4A/A4B implementation slices without changing its outcome: A4A introduces one host-owned, thread-confined Polyglot `Context`, initializes and explicitly enters the registered `protos` language, resolves the exact current `ProtosLanguageContext` through Truffle `ContextReference`, and parses exact Truffle `Source` values through `Env.parsePublic(...)`, which re-enters the A2 `ProtosLanguage.parse(ParsingRequest)` frontend instead of invoking the parser/compiler as a parallel host entry. Execute the resulting language-bound target through the existing explicit `ProtosActivation` and `ProtosRootTaskExecution`, preserving RootActor/task semantics and returning the existing inert `ProtosExecutionOutcome` rather than inventing a temporary Polyglot value model ahead of I026-D. A4B becomes READY for the actual CLI/REPL/bundled-tool/workspace/remaining-driver cutover and retirement of direct `compile(String).call(...)` as a primary production architecture. No public CLI UX, Protos semantics, module identity, Process/Actor semantics, SourceSection/instrumentation, DAP/LSP, native boundary or license terms change in A4A. Implementation version becomes `0.2.266-SNAPSHOT`.

## 0.2.265-SNAPSHOT

- Reconcile closed AUD001 historical wording after the completed AUD001 audit. Rewrite only historical non-transitive-scoping wording that could be misread as saying adjacent AUD001 work was still open, so it now states that those decisions were reviewed independently and were not classified by the referenced decision/publication by transitivity. Preserve AUD001 as CLOSED, preserve every D001-D045 classification and D046 exclusion, and make no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D044 / specification `0.1.381` and close AUD001 on 2026-09-08 after retrospective comparison with Self, Smalltalk/Pharo, Kotlin, Scala, Rust, JavaScript and Ruby plus adversarial and future-scalability review. Retain the ordinary Closure-specific `condition.while(body)` protocol: no `while` keyword or second callback model, ordinary `Object` slot lookup/shadowing, semantic-Closure receiver/body validation, exact pre-test activation order, strict canonical Boolean decisions with fresh Error on every other normal condition result, ignored body results and canonical `null` normal completion. Preserve ordinary Error/non-local-return/suspension/replay/cancellation/`ensure` composition, no implicit Future await/adoption or hidden scheduling/task scope, and D045's independently ratified task-scoped ownership boundary. I023's closed conformance and bounded replay-retention work provide downstream scalability evidence while the specification continues to permit inlining/specialization/compilation. Future loop syntax, `break`/`continue`, value-producing, pattern and async-specific loop facilities remain separate explicit designs. D044 is the final unresolved AUD001 decision; all D001-D045 now have an explicit classification and D046 remains outside the audit. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary, license-term or implementation-follow-up change.

- Complete `I026-A3 — canonical module source identity`. Replace the host module resolver's identity-free `String loadSource(ModuleKey)` boundary with `ProtosModuleSource`, pairing the exact canonical `ModuleKey` with one character Truffle `Source`. Standard-library, bundled-tool and workspace-package file resolvers now retain their exact file URI while delegated sources remain unchanged; all in-repository resolver implementations and tests migrate atomically with no String fallback. Module import, RootActor initial-module execution and bundled-tool staging pass that source object directly into the compiler, preserve it on source-only top-level/derived roots pending A4, and reject a resolver result whose embedded key differs from the requested canonical key. `ModuleKey` remains the sole semantic cache identity and Actor-local caching/cycles/failure behavior is unchanged. I026-A4 becomes READY; CLI/REPL Polyglot cutover, SourceSection/instrumentation, DAP/LSP, normative specification, native boundary and license terms are unchanged by A3.

- Record AUD001 D002 effective-current-form ratification: explicit project-owner ratification of D002 in its effective current form after comparison with Self, ECMAScript, Python, Ruby, Lua, Elixir and Rust plus adversarial, concurrency/distribution and future-scalability review. Retain specification `0.1.348`'s local-slot-only lexical lookup followed by receiver lookup for bare reads, explicit local-only `:` creation, and nearest-existing-local `=` mutation with own-receiver fallback only; retain the owner-approved `0.1.387` correction that fixes the exact assignment destination before RHS evaluation so missing targets fail before RHS effects and RHS effects cannot retarget the write. Preserve failure at a selected non-modifiable binding rather than skipping outward, preserve ordinary control-transfer/write-failure behavior without rollback, and do not reclassify current `super` semantics or adjacent Dxxx decisions by transitivity. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary, license-term or implementation-follow-up change.

- Record explicit project-owner ratification of D019 / specification `0.1.360` under AUD001 on 2026-09-08 after comparison with Erlang/OTP, Akka Classic/Typed, Ray, Orleans and Pony plus adversarial, capability-security, distributed and future-scalability review. Retain the separation of Actor creation genealogy, communication capability and failure/lifecycle authority: `Actor.spawn(...)` gives the creator the new Actor's ActorRef but creates no ambient `parentActor`, creator lookup, reverse ActorRef or durable implicit reply channel in the child. Any capability back to the creator must be explicitly provisioned through ordinary permitted transfer, including passing `Actor.current()` when desired. Keep this absence transitive across bootstrap, Group reconciliation, runtime-coordinated and remote creation; internal genealogy remains implementation metadata rather than program authority, creator termination alone does not terminate the child, and failure authority stays separate. Future supervision, actor-ownership/fate-sharing, link/monitor, durable-service, replacement-following-reference and genealogy-diagnostics facilities remain separate explicit designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change; D018 was reviewed independently under AUD001; this D019 publication did not classify it by transitivity.

- Close `TOOL001-F2E2C — same-capture integration + F2E2 closure`. Add host-internal `ProtosPackageContentVerification` as the mechanical composition gate between the closed B custody and A bundled-Protos verifier: capture one exact already-selected root once, materialize one tool-domain read-only Filesystem over that capture, invoke `self:ContentIdentity.verify` with only inert expected method/algorithm/hex Strings, require the verifier to return exactly the supplied Filesystem, terminate the Package Tool Process before returning the still-open custody, and close custody on every failed path. The verification Process receives no root Filesystem/store authority, Java adds no ContentIdentity policy or tree walker, and later domains rematerialize fresh wrappers over the same immutable capture after source deletion/mutation. Compose the new gate focal with the existing single TOOL001 Protos runner covering the frozen F2E1 vector/negative matrix. F2E2 closes and F2E3 becomes READY; F2E4/F2E5 remain dependency-gated. No normative specification, lock-format, ContentIdentity, capture-backing, PackageExecutionPlan-authority, resolver, public-run, native-boundary or license-term change; implementation version becomes `0.2.264-SNAPSHOT`.

## 0.2.263-SNAPSHOT

- Close `TOOL002-F4C — Future ownership reconciliation`. Record the already-published F4 sequence: F4A removes historical Future-as-unsupported sentinels; F4B1 makes F1/F2 inspection root-preserving and privately proves every deferred Future row; F4B2 atomically activates all retained `future-*` families in the generic bundled-Protos Runner, provisions `executionInspect` in real `protos test`, proves complete selected/passed main-manifest ownership with zero skips, and removes the duplicate Java/JUnit Future manifest owner. Close TOOL002-F/F4 and make TOOL002-G READY. Documentation/governance only: no executable, specification, implementation-version, runtime, native-boundary or license-term change.

- Complete `I026-A2 — canonical Truffle Source compilation boundary`. Connect `ProtosLanguage.parse(ParsingRequest)` directly to the existing Protos parser/canonicalizer/Truffle lowerer using the request's exact character `Source` as the canonical compilation input. Introduce one immutable root factory per source-bound compilation so the top-level root, Closure parameter/body roots and object-body roots all carry the same active `ProtosLanguage` and exact Truffle `Source`; retain that ownership when a Closure execution plan is rebuilt by process-local parallel projection. Keep the legacy direct `compile(String).call(...)` route only as staged pre-A4 machinery and do not treat it as the target architecture. Prove Polyglot `Context.parse(...)` reaches the real frontend without executing user code, and prove exact language/source identity on generated roots and Closure-plan rebuilds. I026-A3 becomes READY. Module source identity, full Polyglot runtime bootstrap/CLI cutover, SourceSection/instrumentation, DAP/LSP, Protos semantics, native boundary and license terms are unchanged by A2.

- Record explicit project-owner ratification of D018's canonical
  Process-bootstrap snapshot identity under AUD001 on 2026-09-08 after
  cross-language, concurrency, isolation, distribution, adversarial and
  future-scalability review. Retain exactly one canonical identity-bearing
  `process.args()` snapshot and one distinct canonical identity-bearing
  `process.environment()` snapshot per logical Process, with repeated successful
  acquisition through same-Process capability/proxy views preserving ordinary
  semantic identity observations. Keep equal-content snapshots from distinct
  Processes semantically distinct; require no permanent physical wrapper,
  address or global registry; and preserve implementation freedom for immutable
  backing sharing, lazy materialization, caching, rematerialization,
  virtualization and scalar replacement when observations remain exact.
  Preserve the existing boundary between canonical acquisition and ordinary
  Actor/P pass-by-value transfer: a transferred snapshot follows the destination
  isolation-domain identity rules rather than being globally canonicalized back
  to the Process snapshot. Record `0.1.361` accurately as the administrative D018
  changelog revision for semantics already published in `PROCESS_IO.md` by
  `8a27fbfb3519126fecf3559a74f9ce62116d6ec6`; no normative specification,
  implementation, blocker, implementation-version, runtime, native-boundary,
  license-term or implementation-follow-up change.

- Record explicit project-owner ratification of D010 / specification `0.1.350` under AUD001 on 2026-09-08 after comparison with Erlang/OTP, Akka Typed, Pony, Node.js, Julia, Rust/Rayon, E/object-capability systems, WASI and Capsicum plus adversarial and future-scalability review. Retain the code-identity-plus-explicit-values Actor bootstrap model through `Actor.spawn(moduleSpecifier, bindingName, arguments...)`, minimal `Actor.current()`, ordinary `ActorRef.send` / `request` / graceful `stop`, and the minimal explicit `SendOperation.cancel` / `retry` control surface without exposing scheduler, mailbox, transport or worker machinery. Retain P as a semantic isolated parallel-execution domain entered through ordinary dispatch (`Closure.parallel`, `Bytes.parallelRange`, `ByteRegion.parallelRange`) rather than introducing a public `P` object, namespace, capability, keyword or syntax. Retain `Process` as an authority-free standard prototype while the actual RootActor Process capability is a host-provisioned initial-module local `process` slot; imports/new Actors acquire no Process authority implicitly, explicit Actor delegation does not amplify authority, Process does not imply Filesystem/network/subprocess/Node/Cluster authority, and Process has no P-transfer contract. Treat D010's historical surface-exhaustiveness wording as scoped to the `0.1.350` publication point rather than as a veto on later independently owned compatible extensions such as `Actor.group(...)` and `ActorRef.termination()`. Future placement/resource Spawner capabilities, supervision/discovery, parallel-executor/QoS facilities, capability attenuation and similar higher-level mechanisms remain separate explicit designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL001-F2E2B — exact selected-root capture + verified-capture host custody` after explicit project-owner approval of the run-scoped same-capture architecture on 2026-09-08 following alternatives, scalability and future-evolution review. Add host-internal `ProtosCapturedFilesystemCustody`: capture one exact already-selected root once through the existing secure I024 NIO machinery, close the source authority immediately, retain the exact immutable captured backend across Package Tool Process termination, rematerialize fresh structurally read-only Filesystem capabilities in later Actor domains over that same backend, and release run-owned custody deterministically and idempotently. Reuse the standard captured-Filesystem read-only adapter rather than making Filesystem Actor-transferable or adding a second namespace model. Keep physical backing representation abstract so future CAS/deduplicated/COW/remote immutable storage can replace today's managed backing without changing this contract. Keep `ProtosPackageExecutionPlan` inert; do not introduce a global registry, PackageId-to-custody binding, source-Path reopen, fetch, store layout, resolver integration or public-run behavior. F2E2C becomes READY while F2E3 remains dependency-gated on F2E2 closure. No normative specification, lock-format, ContentIdentity, native-boundary or license-term change; implementation version becomes `0.2.262-SNAPSHOT`.

## 0.2.261-SNAPSHOT

- Publish `TOOL002-F4B2 — public Future expectation ownership cutover`. Activate all retained `future-integer`, `future-null`, `future-boolean`, `future-error`, `future-error-parent`, `future-cancelled`, and `future-observation-error-identity` rows in the generic bundled-Protos Runner using the already-validated root-preserving `executionInspect` path; provision that generic inspection facility in the real `protos test` session; require the main manifest to be selected and passed completely with zero skipped rows; and retire the duplicate Java/JUnit Future expectation owner in the same executable cutover. Package/TOML execution remains on the ordinary non-inspection path. No normative specification, Future runtime semantics, or native-boundary change. Implementation version becomes `0.2.261-SNAPSHOT`.

## 0.2.260-SNAPSHOT

- Record explicit project-owner ratification of D017 / specification `0.1.357` under AUD001 on 2026-09-08 after retrospective reconstruction, comparison with Erlang/OTP, Akka and Orleans-style Actor lifecycle/supervision models, adversarial review and future-scalability analysis. Retain D017 as the bounded Actor API cleanup: portable `SendOperation` exposes only `cancel()` / `retry()` rather than transport/status/attempt diagnostics; uncertainty never authorizes transparent replay; `ActorRef.stop()` remains the idempotent lifecycle command returning canonical `null` with no dedicated stop Future/operation, while `ActorRef.termination()` remains the independent Future-based lifecycle observation; and Core v0.1 exposes no configurable public supervisor/failure-authority policy API. Preserve incarnation identity strictly: Actor failure terminates that incarnation and any replacement is a distinct Actor/ActorRef rather than an identity-preserving restart. Future Supervisor/Controller, durable service identity, diagnostics/tracing, discovery/rebinding and recovery institutions remain separate explicit designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D016 / specification `0.1.354` under AUD001 on 2026-09-08 after comparison with Erlang/OTP, Akka Typed, Ray, Orleans and Elixir/GenServer plus adversarial, distributed and future-scalability review. Retain `Actor.spawn(...) -> ActorRef` as the sole Core creation result: creator-side resolution/validation/transfer completes before one semantic creation cutover that establishes exactly one Actor incarnation, identity and ActorRef; placement, admission, bootstrap and readiness follow for that same incarnation. Capacity shortage delays `INITIALIZING` progress rather than synchronously failing spawn, authorizing unlimited oversubscription or creating implicit timeout/deadline/creation-cancellation semantics. Add no public `SpawnOperation`, Future, wait, admission or placement handle. Preserve ordinary `ActorRef.stop()`, the existing bounded pre-READY messaging/acceptance rules without a bootstrap mailbox, weak admission fairness when compatible opportunities recur, invisible scheduler/placement/provisioning machinery, and in-flight Group-candidate accounting without premature routing eligibility. Future readiness, placement/reservation, admission-policy, virtual/durable Actor and infrastructure-control facilities remain separate designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary, license-term or implementation-follow-up change; D015 and D017-D019 were reviewed independently under AUD001; this D016 publication did not classify them by transitivity.

- Record explicit project-owner ratification of D015 / specification `0.1.353` under AUD001 on 2026-09-08 after comparison with Erlang/OTP, Java, Smalltalk resumable exceptions and Akka supervision plus adversarial and future-scalability review. Retain one Core non-resumable Error universe inside Actors: selecting a matching handler abandons the `Error.signal()` continuation and unwinds to the enclosing handler boundary; a normal handler result is the result of that `handle` operation and is never substituted back at the abandoned signal point. A handled Error is not Actor failure merely because it occurred, while only an Error that ultimately escapes the Actor turn unhandled triggers the Actor fatal-failure boundary. Preserve already-completed Actor-local mutation, I/O, messaging and other effects under their ordinary contracts: Core performs no implicit rollback, replay or retry, and later retry/compensation is a new ordinary program action subject to the operation's own commitment semantics. An Error signaled by the selected handler follows ordinary outer-handler search rather than reusing that consumed handler destination. Keep synchronous Actor Error escape distinct from asynchronous child-Future failure. D015 does not reclassify failure-authority/supervision policy (including D017), D034 handler validation/timing, D043 ensure/unwind cleanup, D045 structured ownership, or any future explicit restart, resumable-condition, transaction or compensation facility. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Complete `I026-A1` and begin the executable Truffle tooling foundation. Register Protos as a discoverable `TruffleLanguage` with one internal `ProtosLanguageContext` per Polyglot context, add the official Truffle DSL annotation processor required to generate the language provider, and add the current Polyglot API dependency for host-side discovery/initialization. Prove through focused Java integration that the Polyglot engine discovers language id `protos`, exposes its default `application/x-protos` MIME type, and initializes the language context exactly once. Refine the former I026-A plan into A1-A4 so the existing direct `compile(String).call(...)` CLI/runtime path is migration state rather than a compatibility constraint; A2 will connect the real Truffle `Source` parse boundary and A4 will retire the old top-level direct-entry path. No parse/eval support, SourceSection, instrumentation/tag, debugger, DAP/LSP, Protos semantic, native-boundary or license-term claim is added by A1.

- Record explicit project-owner ratification of D014 / specification `0.1.356` under AUD001 on 2026-09-08 after recovered historical owner selection plus renewed cross-language, adversarial and future-scalability review. Retain `size()`, `hash()` and the exposed `identityHash()` convenience as ordinary zero-argument Closure-valued message behavior: ordinary reads such as `obj.hash` retrieve/extract the selected value and never auto-invoke it, while parenthesized forms invoke through the ordinary callable protocol. Preserve ordinary lookup/shadowing, including failure rather than ancestor fallback when a nearer selected slot is non-invokable. Keep `identityHashOf(value)` as the separate non-overridable primitive used by semantic identity machinery and `IdentityMap`; overriding the ordinary `identityHash()` message does not redefine identity hashing. Add no getter/property/descriptor category, auto-call rule, hidden built-in exception or second invocation protocol; future explicit property/computed-slot facilities remain separate designs, and JIT/inlining/allocation-elision remain permitted when observable semantics stay exact. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Publish `TOOL002-F4B1 — root-preserving Future inspection preparation` after the attempted F4 cutover exposed that the historical F1/F2 source wrappers are not a safe full-corpus boundary. Replace only the private F1 resolved and F2 terminal evaluation mechanics with the already-published generic live-result `executionInspect` facility: each retained source now executes unchanged as the fresh child Process entry, drains to cooperative idle, and only then is its still-live Future inspected inside that same Process. Keep public `isDExpectation`/`runSimple` selection and the temporary Java `future-*` owner unchanged. Extend the F1/F2 focals to use inspection and add one pre-cutover Protos fixture that privately evaluates every deferred Future row in the current main manifest, reporting the exact first failing CaseId if coverage is incomplete. No normative specification or native-boundary change. Implementation version becomes `0.2.259-SNAPSHOT`.

## 0.2.258-SNAPSHOT

- Record explicit project-owner ratification of D013 / specification `0.1.352` under AUD001 on 2026-09-08 after comparison with Python, ECMAScript, Kotlin, Scala, Ruby, Lua, Self and Smalltalk/Pharo-style construction plus adversarial and future-scalability review. Retain parenthesized invocation as ordinary delegating lookup of the nearest `call` slot followed by validation that the selected value is a semantic Closure and direct terminal activation of that selected Closure with the original receiver as `this` and slot owner as `methodHome`; do not reinterpret invocation as recursive `call.call...`. Keep `call` an ordinary observable slot with normal inheritance, shadowing, copying, composition, assignment, aliasing and extraction; a nearer non-Closure `call` fails rather than falling through. Keep callability inspection read-only and structural without activation, arity/default work or behavior pinning. Retain ordinary `Object.call` as the shared mechanism for Closure execution and default prototype construction, with standard factories specializing through nearer ordinary `call` slots. Add no hidden callable bit/property, registry, metatable, metaclass, `Callable` hierarchy, `Method` value kind or second dispatch route. D022 remains independently authoritative for its ratified initializer/result details, while future callable typing, overload/multimethod, RPC, partial-application and other callable abstractions remain separate designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary, license-term or implementation-follow-up change.

- Record explicit project-owner ratification of D012 / specification `0.1.355` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain the String normative-ownership migration rather than reclassifying the pre-existing String semantics by transitivity: `semantics/VALUES_AND_COLLECTIONS.md` is the current primary owner of Core String value/indexing behavior, `PROTOS_GRAMMAR.md` owns String-literal source syntax/lexing and mandatory parsing rules, and `io/TEXT_IO.md` owns the standard Encoding/text-to-bytes conversion contract. The legacy monolithic language-spec String headings remain compatibility/navigation anchors rather than independent normative authorities. Keep abstract String values, source spelling and encoded byte representation as distinct semantic responsibilities, and do not use this governance ratification to standardize D020 transformations or to re-ratify historical grapheme, Unicode, identity, equality, concatenation or other String choices. This ownership split does not freeze today's file names or document granularity: a future explicitly approved coherent owner migration may split, merge or rename the normative modules without creating duplicate authority. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL001-F2E2A — captured-Filesystem ContentIdentity canonicalizer/verifier`. Add bundled-Protos `self:ContentIdentity` over an already-captured read-only Filesystem: iterative `entries` traversal, exact portable artifact-path and sibling ASCII-case-fold validation, root regular `protos.toml`, link/other rejection, exact regular bytes through `std:io/Files`, canonical `protos-package-tree-v1` FILE/END framing with arbitrary-precision minimal varuint, `std:crypto/SHA256`, lowercase hex and fail-closed recorded-identity verification that returns the same supplied capture on success. Add Protos-owned fixed-vector/negative evidence by extending the existing single `ProtosPackageToolProtosTest` RootActor-local bridge; no per-corpus Java wrapper is reintroduced. F2E2A closes and F2E2B becomes READY; F2E2C/F2E3 remain dependency-gated. No normative specification, store-layout, capture-custody, execution-plan, resolver or public-run semantic change; implementation version becomes `0.2.258-SNAPSHOT`.

## 0.2.257-SNAPSHOT

- Record explicit project-owner ratification of D008 / specification `0.1.347` under AUD001 on 2026-09-08 after recovered historical owner selection plus renewed cross-language, adversarial and future-scalability review. Retain `Object.without(name)` and `Object.alias(sourceName, aliasName)` as ordinary local-slot structural-view messages: semantic String names only, no delegated lookup/coercion, fresh open ordinary result objects with immediate parent `Object`, shallow preservation of exact stored value identity, `alias` adding rather than renaming, and no Closure cloning/re-homing/rebinding during transformation. Ordinary later lookup/invocation supplies `this`/`methodHome`; delegated bindings, source parent/state, live source-to-view dependencies, Trait/TraitView kinds, trait-specific syntax, deep-copy semantics and precedence rules remain absent. The contract constrains observable semantics rather than physical copying, leaving persistent/COW/shape/structural-sharing and allocation-elision optimizations available. Future Traits/Roles, Symbols/Selectors, bulk structural transforms, multiple delegation and shared-memory policy remain separate designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner approval of `AUD002` Alternative C and close the GraalVM/Truffle editor-tooling architecture audit. Select the hybrid Truffle-first direction: make the Protos runtime a properly registered/instrumentable Truffle language, preserve source identity into exact source sections, expose semantically faithful instrumentation/scopes/value interop, prove GraalVM DAP and dynamic-LSP reuse on real Protos execution, reuse the real Protos parser/resolver for later static language intelligence, and keep editor integration thin rather than duplicating Protos semantics. Allocate `I026` READY for the bounded runtime/compiler tooling foundation and its DAP/LSP proof gates; do not allocate `TOOL003`, public CLI UX, a VS Code extension, or a static language-service item yet. Documentation/governance only: no normative specification, implementation source, implementation version, runtime coordinate, native-boundary or license-term change.

- Record explicit project-owner ratification of D011's effective public Filesystem/I/O surface under AUD001 on 2026-09-08 after comparison with WASI/capability filesystem design, Rust and Deno open options, Java NIO/Charset, Python pathlib/codecs, and wrapper-lifecycle approaches in Java, Rust and Go plus adversarial/future-scalability review. Retain authority-free portable Path values, explicit non-ambient Filesystem capabilities, optional bootstrap-local Root filesystem provisioning, the closed local-only Boolean open-options object with invocation-time snapshot and fail-closed validation, portable host-independent Path construction, authority-free Encoding descriptors, and borrowed-by-default / explicit-owning byte/text wrappers. Record D011 in its effective current form rather than pretending specification `0.1.351` is byte-for-byte current: independently ratified D028 / `0.1.369` already superseded the original Path construction selector `parent()` with `parentComponent()` while preserving ordinary `parent()` delegation reflection. D028 is not reopened. Future private/virtual Filesystem creation, additional open options/capabilities, codec registries, convenience Path parsing and native-path boundaries remain separately designable. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary, license-term or implementation-follow-up change.

- Open `AUD002` and record the GraalVM/Truffle editor-tooling compatibility audit. Establish that current Protos execution already uses Truffle AST/call-target machinery and preserves `SourceSpan` offsets, but is not yet a registered/instrumentable `TruffleLanguage`: roots use a null language, file/compiler APIs discard Truffle source identity, and no source-section/tag/scope/value-interop bridge or CLI DAP/LSP integration exists. Record GraalVM DAP as a high-reuse debugging candidate and GraalVM LSP as useful dynamic augmentation rather than a complete static Protos language server. Recommend a hybrid Truffle-first architecture while leaving that durable choice explicitly PENDING PROJECT-OWNER APPROVAL and allocating no `TOOL003` or implementation slice. Documentation/governance only: no normative specification, implementation version, runtime coordinate, native-boundary or license-term change.

- Record AUD001 D009 as RATIFIED from recovered explicit project-owner approval of specification `0.1.349`, reaffirmed on 2026-09-08 after comparison with Java/JVMS, ECMAScript abstract-operation reuse, WebAssembly specification layering and Rust plus adversarial/future-scalability review. Retain one primary normative owner for each observable rule; other normative documents may reference it, define genuine domain-specific specializations, or own additional cross-domain interaction semantics without duplicating the complete contract as a second authority. Keep Grammar authoritative for syntax/mandatory lowering rather than neighboring semantics and keep the current `runtime/ABSTRACT_RUNTIME.md` informative. Do not freeze today's physical document split: explicit ownership transfers, splits/merges, generated combined views and a future explicitly approved formal normative semantics remain possible while conflicting duplicate authority remains forbidden. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D005 / specification `0.1.345` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain the general Error occurrence/identity policy: every independent standard failure occurrence produces one fresh ordinary Error identity whose immediate parent is the normatively promised Error prototype, while standard Error prototypes remain shared category/protocol objects and are never implicit singleton runtime-failure instances. Signaling or re-signaling an already-existing Error preserves exactly that object, and a same-domain recorded failure outcome preserves its exact recorded Error; accordingly a FAILED Future re-signals its stored Error, while a CANCELLED Future stores cancellation state only and each `value()` observation creates a fresh `Cancelled` occurrence. Error identity remains ordinary and domain-local; boundary reconstruction follows ordinary transfer semantics and Actor-fatal Errors do not implicitly cross Actor boundaries. Retain non-resumable `Error.signal()`, delegation-based category/handler matching and D005's deliberately shallow portable taxonomy, including its I/O family; host errno/status distinctions do not become Core ancestry unless separately standardized, and retry safety remains owned by operation commitment/effect contracts rather than Error category. Physical allocation, diagnostics/tracing, future recovery/restart facilities, later standard Error prototypes and later handler/cleanup decisions remain separately owned. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D007 / specification `0.1.346` under AUD001 on 2026-09-08 after corrected cross-language, adversarial, package-system and future-scalability review. Retain `import(specifier)` as an ordinary call whose already-evaluated argument must be exactly a semantic `String`: no coercion, formatting, `toString` dispatch, duck typing, implicit invocation or String-like delegation may manufacture a specifier, and an invalid non-String value fails with the ordinary Core `Error` before host resolver entry. Core passes the exact String text to the host/module resolver without assigning intrinsic path, URI, URL, package, filesystem or registry meaning, without rewriting/normalizing it, and without universally rejecting the empty String. Host policy may interpret or reject that exact text, but a successful resolution produces the canonical `ModuleKey` that owns subsequent Core module identity/cache semantics; original spelling is not module identity, so distinct spellings may converge to one key and one Actor-local module instance. Resolution failure exposes a Protos `Error`, not host exceptions, status codes or sentinel values, and creates/caches no module instance. Specifier validation/resolution introduces no hidden Protos suspension, Future, scheduling or re-entry boundary. Package discovery, dependency/version selection, registry/network acquisition, immutable artifact fetching and execution-plan preparation remain explicit host/tooling concerns before or outside ordinary Core import; a future genuinely asynchronous acquisition or dynamic-loading capability remains separately designable rather than retroactively changing `import`. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D006 / specification `0.1.344` under AUD001 on 2026-09-08 after cross-language comparison spanning Python, Julia, Swift, ECMAScript BigInt, Rust and Self plus adversarial and future-scalability review. Retain exact unbounded ordinary `Integer`, IEEE 754-2019 binary64 `Float`, the eight fixed-width signed/unsigned integer semantic families, strict same-family standard arithmetic with no implicit promotion/coercion, explicit checked numeric conversions, checked fixed-width overflow, exact-rational-to-binary64 integer `/`, same-family exact-integer `div`/`mod`/`%`, exact cross-family numeric equality/ordering, family-sensitive semantic identity and hash coherence with numeric `==`. Keep `SmallInteger`/`BigInteger`, tagging, boxing and other storage representations unobservable, while Rational, Decimal, Complex, bit/wrapping protocols, relaxed/SIMD math and any future promotion framework remain separate explicit designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Apply the explicitly owner-approved AUD001 D002 correction for bare assignment target timing. Preserve D002's lexical-local-first lookup, receiver fallback, local-only assignment destination and create-vs-modify separation, but make `x = rhs` select its exact existing destination before evaluating `rhs`; a missing destination therefore signals `SlotNotFound` before RHS effects, and a successfully selected destination is never re-resolved after RHS effects create or shadow same-named slots. This aligns the normative contract with the evaluator behavior that already existed when D002 was published and remains implemented today. Add Protos-level conformance cases for non-retargeting and missing-destination-before-RHS behavior. Specification/test correction only: no production implementation, implementation version, runtime/native boundary, license terms or AUD001 final classification change; D002 remains pending the separate governance-ratification slice after this normative correction is published.

- Record explicit project-owner ratification of D004 / specification `0.1.342` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain the standard Boolean protocol as ordinary one-argument messages `ifTrue`, `ifFalse`, `and` and `or`: when standard behavior is selected, the original receiver is exactly canonical `true` or `false`, while custom objects may define or override the same selectors without becoming semantic Booleans or introducing language-wide truthiness. Receiver and argument expressions keep ordinary call evaluation; only a selected callback is callability-validated and invoked, exactly once with zero positional arguments through ordinary polymorphic invocation, and it need not be a Closure. Standard `ifTrue`/`ifFalse` return the selected callback's exact normal result or canonical `null` on the unselected path. Standard `and`/`or` retain short-circuit behavior and require an invoked callback to return exactly canonical `true` or `false`, with no truthiness conversion, coercion, implicit invocation, implicit awaiting or Future adoption. Error/non-local/cancellation/other non-normal control transfers propagate ordinarily; the Boolean protocol adds no hidden task, lock, suspension or scheduling boundary. Keep the mandatory operator lowering `a && b -> a.and(() => b)` and `a || b -> a.or(() => b)` as ordinary dispatch so a custom receiver may observe the generated RHS Closure. Implementations may specialize canonical Boolean cases only when all observable lookup/evaluation/invocation/result/control behavior remains exact. Future conditional sugar and additional callable kinds remain separately designable. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Ratify AUD001 D003 as amended and publish specification `0.1.386` after explicit project-owner approval on 2026-09-08. Retain the complete deterministic Closure positional argument/default/rest/spread binder from specification `0.1.340`, but require all required non-rest parameters to precede defaulted parameters, with optional rest final; the former default-before-required shape is rejected because positional-only invocation cannot use that default in any otherwise successful call. Add no named arguments, omission marker, hidden missing/undefined value, TDZ, second parameter namespace, or runtime binder change. Allocate `I025` READY for parser/conformance alignment. Specification/governance only: no implementation-version, native-boundary or license-term change.

- Record explicit project-owner ratification of D001 / specification `0.1.341` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain one general semantic `Sequence` normal-result rule: expressions evaluate strictly left to right; a non-empty Sequence that completes normally returns the exact result of its final expression, while a zero-expression Sequence that completes normally returns canonical `null`. Error signaling/unwind, non-local return, cooperative cancellation and other control transfers are not converted into `null` and produce no normal Sequence result when they leave the Sequence. Apply the rule uniformly to semantic expression-sequences such as source module/program bodies and braced Closure bodies, while keeping `object-body-sequence` and object-construction results under their independent object-model semantics. Introduce no `Unit`, `undefined`, EmptySequence value, receiver/context-dependent empty result or runtime failure merely for emptiness. The rule constrains observable semantics rather than representation, so implementations may erase, inline or constant-fold Sequence machinery when behavior remains exact. Any future static type-system `Unit`/no-useful-result abstraction remains separate design work rather than a retroactive reinterpretation of D001. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D021 / specification `0.1.358` under AUD001 on 2026-09-08 after distributed-systems, object-capability, adversarial and future-scalability review. Retain the three-way identity boundary: target Group identity, semantic `GroupRef` identity and physical proxy/wrapper/address/cache representation are distinct. A particular acquired `GroupRef` remains one identity-bearing communication capability across ordinary Actor/Process transfer, round-trip return and rematerialization; every materialization of that same semantic capability preserves `===`, `identityHashOf(...)` and `IdentityMap` key identity even when the runtime uses different physical proxies. Independently acquired GroupRefs are not canonicalized merely because they denote the same Group or currently carry equivalent effective communication permissions; different effective restriction state necessarily denotes different GroupRef identity. Preserve implementation freedom: no global interning, permanent wrapper registry, stable address, Group-to-GroupRef registry, proxy lifetime tied to Group lifetime or network coordination is required. Group membership/routing evolution, Group lifetime, Authority, future attenuation/revocation, discovery/reacquisition and any future explicit target-equality/joining facility remain separate designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D024 / specification `0.1.367` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain fresh semantic identity for every successful receiver member-read selecting a Closure: each extraction produces a distinct ordinary Closure carrying the original receiver and current selected lookup home/`methodHome`, even when receiver, slot, stored Closure and home are unchanged. Preserve exact identity under ordinary alias/reference-preserving operations; storing an extracted Closure does not mutate or rebind it, while a later member-read of a Closure-valued slot is a new ordinary extraction with fresh identity and the later read's binding. Keep primitive identity/hash/IdentityMap behavior ordinary, distinguish observable extraction from immediate message invocation so unobservable wrappers need not be materialized, and introduce no Method/BoundMethod kind, Closure prototype, canonicalization cache, interning/global identity registry or bound-method-specific equality. Retaining one extraction remains the explicit way to retain stable callback identity. Future callable/behavior reflection, callback subscription facilities, multiple-delegation dispatch metadata, cross-isolation Closure transfer and physical COW/persistent representation remain separate work. D025/D026 are not classified by this slice and D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-D — cross-environment conformance + closure` and the parent `DIST002` project. Record green post-D3 GitHub Actions evidence on revision `6977f06441624267147b1f146fac340d06f464c3`: Tests run 34224766060 and Distribution snapshot run 34224766104 both SUCCESS, with distribution completing bootstrap/checksum, workspace trust, zero-drift toolchain verification, full Maven tests, portable build, complete extracted-distribution gate, checksum preparation and artifact upload. The closure publication gate revalidates the current descendant publication candidate locally with the exact canonical JDK/Graal/Truffle/Maven contract, toolchain drift 0, full tests and portable distribution validation. Documentation/status closure only: no Protos semantics, specification, implementation version, runtime coordinates, native boundary, license terms or historical `v0.2.236` release change.

- Complete the temporary TOOL001 per-corpus Protos fixture-wrapper consolidation with Runner-D3. Move the remaining `ProtosNioReadOnlyFilesystemBackendTest` fixture execution into `ProtosPackageToolProtosTest`, preserving exact-name read-only authority, final-symlink no-follow and unsupported-provider fail-closed evidence while switching execution to RootActor-local `ProtosRootTaskExecution`. Remove the redundant wrapper and record that F2E2A must extend this single runner rather than recreate a Java ContentIdentity wrapper. Separate Java tests for genuine adapter/backend/preflight mechanics remain independent. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Record explicit project-owner ratification of D022 / specification `0.1.362` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain the inherited standard `Object.init()` normal result as its exact receiver (`this`) on zero-argument normal completion; the inherited standard behavior still rejects a non-empty argument vector with the ordinary argument-count Error. Keep overriding `init` methods under ordinary Closure return semantics, so an override is not required to return `this`. Preserve default construction separation: inherited `Object.call` creates one fresh child whose parent is the invocation receiver, sends ordinary `init` to that fresh instance with the supplied arguments, ignores any normal result from `init`, and returns the fresh instance; an Error or other control transfer from initialization propagates and construction has no successful instance result. Direct `init()` therefore remains useful and portable without turning `init` into a factory-return channel or introducing a special initializer value category. Alternative constructors remain ordinary named messages. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Continue TOOL001 single-Protos-runner migration with Runner-D2. Add the existing `resolution-input` manifest corpus to the shared `plain` profile, move only its two physical lock stale/fresh host preparations into `ProtosPackageToolProtosTest`, reuse the existing confined metadata Filesystem fixture, and remove the redundant `ProtosPackageToolResolutionInputTest` wrapper. All Protos sources remain unchanged and execute through `ProtosRootTaskExecution`. Read-only metadata backend evidence and F2E2A remain for later bounded slices. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Record explicit project-owner ratification of D023 / specification `0.1.363` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain one general normal-result rule for the four Core slot-write expression forms `x: rhs`, `object.x: rhs`, `x = rhs`, and `object.x = rhs`: after successful slot creation or assignment, the expression returns the same exact object produced by RHS evaluation and stored by that write. Preserve exact identity without a second slot read, implicit conversion, copying, canonicalization, wrapping, receiver substitution or canonical `null` result. A write that does not complete normally has no normal expression result and does not roll back effects already completed during target/RHS evaluation. Destination-selection, delegation and object-state validity remain owned by their existing rules. Indexed `object[index] = value` remains outside D023 under the indexing protocol's own contract, and future computed-property/setter facilities remain separately designable rather than silently redefining slot writes. The exact-RHS rule constrains observable semantics, not storage/JIT representation: implementations may reuse the same SSA/runtime value, elide unused results and optimize slot storage provided identity and failure/control-transfer behavior remain exact. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-D3 — CI distribution workspace Git trust correction` after post-D2 revision `fab881f88b9bf89b36326bc21aeaba7257a73872` proved checksum bootstrap, checkout, zero-drift JDK25/Graal-Truffle25.3.4.1/Maven3.9.9 validation and the full 1096-test suite all pass, while Distribution snapshot 34223295074 fails only when `dist/build_portable.py` invokes Git under the root job container after `actions/checkout`'s temporary HOME loses its `safe.directory` registration. Keep root only for prerequisite provisioning, explicitly trust exactly `$GITHUB_WORKSPACE` in the real job HOME immediately after checkout, and prove the builder's exact `git status --porcelain=v1 --untracked-files=all` operation before continuing. Do not use `safe.directory=*` or weaken source-identity checks. DIST002-D remains READY pending green post-D3 Tests + Distribution snapshot evidence and final reconciliation. Project-tooling/CI correction only: no Protos semantics, specification, implementation version, JDK/Graal/Truffle/Maven coordinate, runtime/distribution contract, native-boundary, license-term or historical `v0.2.236` release change.

- Continue TOOL001 single-Protos-runner migration with Runner-D1. Migrate only the existing `metadata-publication` Protos corpus into the already-published `ProtosPackageToolProtosTest`, reusing Runner-C's confined metadata Filesystem fixture and host postconditions; remove the redundant `ProtosPackageToolMetadataPublicationTest` wrapper. Keep all Protos fixture sources unchanged and execute them through `ProtosRootTaskExecution`. `resolution-input`, read-only metadata backend evidence and F2E2A remain for later bounded slices. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Record explicit project-owner ratification of D025 / specification `0.1.366` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain the Core v0.1 ownership of the standard Closure-specific selectors `future` and `parallel` as ordinary local Closure-valued slots of `Object`, reached by Core Closures through the independently ratified D027 direct delegation edge to `Object`. Introduce no standard `Closure` or `Callable` prototype, hidden Closure method table, per-Closure materialization or second dispatch path. The selected standard behaviors retain the semantic Closure receiver domain: another receiver may find the inherited selector by ordinary lookup, but invocation fails with the ordinary invalid-receiver Error before asynchronous/isolated-parallel effects and does not resume lookup. Preserve ordinary non-reserved selector naming, local shadowing/override behavior and the existing receiver-bound Closure extraction/methodHome rules. Keep operation semantics modular: FUTURES_AND_TASKS owns `future()` after receiver validation and PARALLEL_EXECUTION owns `parallel(...)` after validation; scheduler, worker and host machinery remain unobservable implementation details. This ratification is deliberately bounded to D025 `future`/`parallel`: it does not ratify D024 or D026, does not claim D025 as the owner of later `ensure`/`while` decisions, does not grant `future`/`parallel` to every ordinarily invokable object, and does not prohibit a future explicitly approved `Closure`/callable abstraction if such a category later earns its place. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Continue TOOL001 single-Protos-runner migration with Runner-C. Move only the `manifest-command` and `lock-file` host setup/postcondition methods into the existing `ProtosPackageToolProtosTest` bridge, preserve their existing confined metadata Filesystem authority and Protos fixture sources, execute every source through `ProtosRootTaskExecution`, and remove the two redundant Java wrapper classes. `metadata-publication`, `resolution-input`, read-only metadata backend evidence and F2E2A remain for later bounded slices. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Record explicit project-owner ratification of D026 / specification `0.1.364` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain exactly the canonical Boolean values `true` and `false` as the Core v0.1 Boolean semantic family, with no standard prelude binding, object or prototype named `Boolean`. Keep `Boolean` an ordinary non-reserved identifier: a program or library may bind that name through ordinary mechanisms, but the binding does not confer Boolean-family membership, and delegation/copying/composition likewise do not manufacture another Boolean value. This does not introduce truthiness, a Boolean conversion constructor, nominal Boolean type metadata or wrapper objects. D027's separate portable parent/topology decision is not classified by this slice; future Boolean libraries or family-introspection facilities remain separately designable. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D028 / specification `0.1.369` under AUD001 on 2026-09-08 after cross-language comparison and dedicated future/scalability review. Preserve `parent()` as the general Core structural-reflection selector for the receiver's immediate delegation parent and keep standard `Path` from overloading or shadowing that selector. Retain `parentComponent()` as the distinct immutable Path constructor that appends one semantic parent-traversal component without resolving a Filesystem, computing a lexical parent, dropping a preceding component, or lexically collapsing the component; retain no Core v0.1 `Path.parent()` traversal compatibility alias. This keeps object reflection uniform and preserves structural Path semantics for capability-confined resolution across backend indirection. Future Path parsers/literals, lexical-parent/drop-last operations, normalization, first-class component APIs, multiple-delegation reflection and persistent/rope representations remain separate work; the current component-list copying implementation is not ratified. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-D2 — CI checksum metadata EOF correction` after post-D1 GitHub Actions runs Tests 34222276908 and Distribution snapshot 34222276919 on revision `4f0b608728eadad569372d17eade6edb251737ec` both failed before checkout: Apache Maven 3.9.9 serves its exact 128-hex `.sha512` body without a trailing newline, so shell `read` returns non-zero at EOF under `set -e` even after populating the digest variable. Preserve D1's exact digest-only authority, 128-hex validation and independently calculated SHA-512 equality check while loading the complete metadata body with successful command substitution. DIST002-D remains READY pending green post-D2 primary workflow evidence and final cross-environment closure. Project-tooling/CI correction only: no Protos semantics, specification, implementation version, JDK/Graal/Truffle/Maven coordinate, runtime/distribution contract, native-boundary, license-term or historical `v0.2.236` release change.

- Continue TOOL001 single-Protos-runner migration with Runner-B. Add one shared `project-tree` profile to the already-published `ProtosPackageToolProtosTest`, migrate only the `resolution-root` and `execution-plan` corpora, preserve their existing read-only `ProtosNioReadOnlyTreeFilesystemBackend` confinement and `projectTreeFilesystem` activation slot, and remove the two redundant Java wrappers. All fixture execution continues through `ProtosRootTaskExecution`; Filesystem-mutable/metadata and F2E2A tests remain outside this slice. Test/governance refactor only: no production source, specification, implementation version, native-boundary or license-term change.

- Close `TOOL002-F3E4 — F3 Future-observation reconciliation`. Reconcile the published F3A/F3B/F3C/F3D and F3E1/E2/E3A/E3B evidence without executable changes: the retained stored/fresh `future-observation-error-identity` policy is complete behind one private bundled-Protos entry, malformed policy fails closed, valid identity/parent mismatches are inert false evidence, and public `isDExpectation`/`runSimple` plus Java's temporary direct `future-*` owner remain unchanged. Close F3E and F3 and make F4 READY for the atomic public Future expectation ownership cutover. Documentation/governance only: no specification, implementation-version, runtime, native-boundary or license-term change.

- Start TOOL001 single-Protos-runner migration with Runner-A. Add one temporary `ProtosPackageToolProtosTest` Java bridge and one small runner manifest, migrate only the existing `version` and `lock` Protos corpora into it, and remove their two per-corpus Java wrappers. Execute fixtures through `ProtosRootTaskExecution` so the shared runner already has valid RootActor-local task semantics for later Future-suspending corpora. Record that additional TOOL001 Protos corpora must migrate into this same runner in bounded slices until TOOL002 replaces the bridge. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Make cost-aware task decomposition continuous rather than a one-time pre-implementation check. If an initially bounded task becomes materially costly, slow, context-heavy, failure-prone, or repeatedly expands/reworks the same surface, require the agent to stop growing the current slice and automatically subdivide the remaining already-approved implementation work whenever independently valid boundaries exist. Decomposition itself needs no user approval when parent outcome, approved semantics, priorities, and dependency order are preserved; user approval remains required when the split exposes a substantive design choice, changes outcome/priority, requires an invalid intermediate state, or selects among materially different project-level dependency orders. Governance only: no specification, implementation version, runtime, native-boundary or license-term change.

- Close `DIST002-D1 — CI bootstrap checksum portability correction` after observed GitHub Actions runs Tests 34221407013 and Distribution snapshot 34221407000 for exact DIST002-C commit `996fed0393c15b122bec6541bf8394bdb15a96c8` both failed in the pre-checkout Maven bootstrap: Apache Maven 3.9.9 publishes its `.sha512` as a bare 128-hex digest, which GNU `sha512sum -c` cannot consume without a filename. Preserve exact Maven 3.9.9 and SHA-512 integrity while making both workflows parse/validate the digest token, independently hash the downloaded archive, compare the values, and emit `MAVEN_ARCHIVE_SHA512_CHECK: PASS` before extraction. DIST002-D remains READY pending green post-D1 primary workflow evidence and final cross-environment closure. Project-tooling/CI correction only: no Protos semantics, specification, implementation version, JDK/Graal/Truffle/Maven coordinate, runtime/distribution contract, native-boundary, license-term or historical `v0.2.236` release change.

- Close `TOOL002-F3E3B — integrated private observation negatives`. Add Protos-owned evidence through the already-private unified `evaluateFutureObservation` entry: malformed family/policy is rejected before the inspector executor can run; stored-mode identity and immediate-parent mismatches complete normally with false evidence; fresh-mode reused identity and immediate-parent mismatches likewise complete normally with false evidence. No bundled Runner, Java host mechanism, public `runSimple`/`isDExpectation`, retained main-manifest, specification or implementation-version behavior changes. Close E3B and make documentation-only E4 READY; F4 remains dependency-gated. Test-impact only.

- Record explicit project-owner ratification of D027 / specification `0.1.365` under AUD001 on 2026-09-08 after comparison with prototype/class-based object models, adversarial review and dedicated future-scalability analysis. Retain the Core v0.1 portable delegation-topology rule: every Core-standard visible object whose immediate parent is not fixed by a more-specific normative owner delegates directly to `Object`, while `Object` remains the unique root with no parent. Preserve explicit semantic exceptions such as the numeric hierarchy, Error taxonomy, Context ancestry, semantic String/Future value parentage, factory-result parentage and domain-owned capability prototypes. Canonical `true`, `false`, `null` and every Closure continue to delegate directly to `Object`; Core v0.1 adds no organizational `Boolean`, `Closure`, `Value`, `Collection`, `Callable` or `AsyncValue` ancestor merely to classify values. Standard `Array`, `Map`, `IdentityMap`, `Future` and other ordinary Core prototype objects likewise use direct `Object` parentage unless a narrower owner says otherwise, and `IdentityMap` does not implicitly inherit `Map`. Keep semantic-family membership separate from delegation and forbid implementation-defined Protos-visible intermediate ancestors while allowing arbitrary hidden host/JIT representation. This ratification owns the Core v0.1 topology only: future explicitly approved prototype categories or a future object-model redesign remain possible normative changes, and D025/D026 were reviewed independently under AUD001 and were not ratified by this D027 publication by transitivity. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-C — live distribution/runtime migration`. Migrate the root Graal/Truffle implementation dependencies and portable runtime closure from historical 24.0.0 to the DIST002-A canonical 25.3.4.1 line while retaining Java 21 bytecode compatibility; advance implementation version `0.2.256-SNAPSHOT` -> `0.2.257-SNAPSHOT`. Move distribution snapshot CI from on-demand GraalVM JDK22 setup to the exact primary GraalVM `25i3` / JDK `25.0.4.1` container with checksum-verified Maven 3.9.9, publish exact JDK/Graal/Truffle runtime metadata, enforce the recorded Java version in the distributed launcher, preserve GraalVM-required multi-release/service metadata in the shaded JAR, and make B4B/B5 use the already-provisioned primary runtime while still proving exact `HotSpotTruffleRuntime`. Extend the toolchain auditor/tests so every live development/CI/distribution binding is zero-drift. Preserve the existing public `v0.2.236` artifact/tag/claims and all DIST001/PERF JDK22/Truffle24 evidence as historical evidence. DIST002-C closes and DIST002-D becomes READY. No Protos normative semantics, specification revision, native-Closure boundary or license-term change.

## 0.2.256-SNAPSHOT

- Record explicit project-owner ratification of D029 / specification `0.1.368` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain the general Core-standard result-family rule: whenever an operation's normal result or Future resolution is specified simply as `Integer` without naming a more specific numeric family, the result is an ordinary unbounded Integer. This is a result-only rule and does not narrow input domains; contracts that explicitly name a fixed-width or other more-specific numeric family remain unchanged. Preserve the separation between semantic family and implementation representation: tagged/machine-word/small/big forms may optimize ordinary Integer values invisibly, while host pointer/index width must not select a portable result family. This avoids per-API `size_t`/Int32/Int64-style host leakage without inventing Size/Natural/Index families; genuinely width-significant APIs remain free to name `UIntN`/`IntN` explicitly. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Publish `TOOL002-F3E3A — unified private Future-observation composition`. Add one private bundled-Protos `evaluateFutureObservation` entry for the retained `future-observation-error-identity` family. It parses the already-closed `MODE:ErrorPrototype` policy once, dispatches `stored` to the existing stored evaluator and `fresh` to the existing fresh evaluator, and returns the selected evaluator's inert result tuple unchanged. Add one Protos-owned composition fixture that exercises both retained positive cases through this single private entry; Java only adds bootstrap-local retained stored-source text alongside the already-present fresh source. Close F3E3A and make F3E3B READY. Do not add integrated malformed/mismatch negatives, public `runSimple`/`isDExpectation` selection, main-manifest migration, or Java Future-owner retirement. No normative specification or native-boundary change; implementation version becomes `0.2.256-SNAPSHOT`.

## 0.2.255-SNAPSHOT

- Record explicit project-owner ratification of D030 / specification `0.1.372` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain `Future.then(transform)` against the ordinary invocation/callability protocol rather than a Closure-only or hidden Callback category. After ordinary receiver/argument evaluation, validate the exact transform eagerly by read-only ordinary `call` lookup before creating any destination Future, continuation task, registration or scheduling state, independently of whether the source Future is pending, resolved, failed or cancelled. The validation does not invoke the transform and does not preflight declared arity/default/rest behavior. It also does not pin the Closure selected during inspection: when a resolved source later runs the continuation, ordinary polymorphic invocation performs a fresh `call` lookup, so legitimate intervening mutation or shadowing remains observable. Programs that want to preserve an already-selected behavior can do so explicitly through ordinary Closure/member extraction rather than a `then`-specific capture rule. Keep D030 distinct from the separately ratified D034 Closure-only dynamic Error-handler boundary. Future executable value kinds, static signature/arity introspection, distributed continuation transfer and other callable institutions remain separate explicit designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D031 / specification `0.1.368` under AUD001 on 2026-09-08 after comparative, adversarial and future-scalability review across JavaScript Promises, Java CompletableFuture/CompletionStage, Python asyncio/coroutines, Kotlin Job, .NET Task/ValueTask and Rust Future models. Retain D031 as the idempotent-lifecycle specialization of the already-ratified D036 Future-result identity rule: every successfully dispatched invocation of a standardized Future-returning idempotent lifecycle operation produces its own fresh semantic standard Future unless that operation expressly returns an existing Future, while repeated `close()`, `shutdownRead()` and `shutdownWrite()` calls observe one irreversible logical lifecycle rather than starting independent attempts. Pending and later terminal observers share that lifecycle's single logical success/failure outcome; where the lifecycle records one terminal Error cause, same-domain re-observation preserves that exact Error object. Fresh Future identity remains semantic rather than a physical-allocation mandate, so follower/completion representation, virtualization, scalar replacement, pooling and shared immutable terminal backing remain implementation freedoms when Future identity and Future-local effects stay exact. Add no canonical lifecycle Future, Future subtype, wrapper, hidden lifecycle token or distributed Future identity. A future API may still explicitly return a pre-existing/shared Future under D036's existing exception. The current follower-list implementation may be optimized independently without changing these semantics. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Publish `TOOL002-F3E2 — positive private fresh observation evaluator`. Extend the private F3E1 policy tuple with its already-validated standard Error prototype name, preserving the existing mode/parent accessors, then add one bundled-Protos `fresh` evaluator over generic same-Process `executionInspect`. The inspector invokes the retained zero-argument `observe` Closure exactly twice, requires both calls to signal, requires each caught Error's immediate parent to be the parsed named standard prototype, and requires distinct Error identities. Add one Protos-owned positive fixture over retained `future/cancelled-value-fresh-error.protos` with `fresh:Cancelled`; Java only installs the existing inspection facility and supplies retained source text. Close F3E2 and make F3E3A READY. Do not add unified stored/fresh dispatch, integrated negatives, public `runSimple`/`isDExpectation` selection, main-manifest migration, or Java Future-owner retirement. No normative specification or native-boundary change; implementation version becomes `0.2.255-SNAPSHOT`.

## 0.2.254-SNAPSHOT

- Record explicit project-owner ratification of D020 / specification `0.1.359` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain the deliberately narrow Core v0.1 String transformation boundary: `uppercase()` and `replace(...)` are not standard Core String operations. String immutability remains the relevant Core rule, while ordinary libraries, implementation extensions and user objects may define those names through normal slots and invocation. Do not import a mandatory Core policy for Unicode case mapping/version updates, locale tailoring, normalization, literal-versus-pattern matching, overlap, empty-pattern/needle behavior, replacement callbacks or streaming/resource behavior merely to provide familiar conveniences. This boundary does not reject future standard text algorithms: casing, case folding, replacement, split/join, normalization, collation, locale, pattern facilities and streaming transformations remain separately designable Standard Library or later-version work with their own contracts. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Publish `TOOL002-F3E1 — Future observation MODE:ErrorPrototype parser`. Add a private bundled-Protos parser for the retained `future-observation-error-identity` expected payload. It requires exactly one non-empty `MODE:ErrorPrototype` separator, accepts only the already-retained `stored` and `fresh` modes, resolves the named immediate parent through the existing standard Error prototype resolver, and fails closed on malformed mode/payload or unknown prototype. Add one Protos-owned fixture covering valid `stored:Error`, `fresh:Cancelled`, and another valid standard parent plus malformed format/mode/prototype cases; the Java harness supplies only ordinary Test Tool bootstrap mechanics. Record the F3E decomposition with E1 CLOSED and E2 READY. Do not add fresh evaluation, unified observation evaluation, public `runSimple`/`isDExpectation` selection, main-manifest migration, or Java Future-owner retirement. No normative specification or native-boundary change; implementation version becomes `0.2.254-SNAPSHOT`.

## 0.2.253-SNAPSHOT

- Record explicit project-owner ratification of D032 / specification `0.1.370` under AUD001 on 2026-09-08 after comparative review across JavaScript, Python, Ruby, Smalltalk, Self, Java/.NET and Go plus dedicated future-scalability analysis. Retain the published `slotNames()` reflection-result contract: every successful invocation returns one fresh identity-bearing ordinary standard Array snapshot, including repeated observations of an unchanged receiver and empty results; independently returned Arrays remain distinct under semantic identity and have independent indexed mutable state. Mutating one snapshot cannot mutate the reflected receiver, another snapshot or a later observation. Preserve the existing shallow contents and deterministic ordering owned by the prior reflection contract, with no deep-copy requirement for contained slot-name Strings. Fresh identity is semantic rather than a physical-copy mandate: lazy, virtual, persistent, shared immutable backing, copy-on-write and allocation-elision strategies remain conforming when observable identity and state independence are preserved. Keep `slotNames()` as ordinary detached data rather than a live view, Mirror, iterator-only surface or implementation-visible cache. Future incremental reflection or explicit reflective-authority facilities remain separate design work. This AUD001 classification does not assert that the current runtime implementation surface is complete; any `slotNames()` implementation/reconciliation gap remains separate implementation work. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D034 / specification `0.1.372` under AUD001 on 2026-09-08 after comparative, adversarial and future-scalability review. Retain `Error.handle(body, handler)` as a deliberately Closure-only dynamic-control boundary: ordinary receiver/body/handler expressions evaluate left-to-right first; body is then Closure-validated, then handler; and no dynamic handler frame is installed until both validations succeed, so invalid body/handler input cannot start the protected body. Preserve ordinary activation-time arity, default and rest binding rather than adding D034 signature preflight; when a handler is selected its frame is consumed/inactive before handler invocation and the exact signaled Error is passed to that Closure. Keep this intentionally distinct from D030's ordinary-invokable `Future.then` callback domain: D034 adds no Handler type, hidden callback category, duck typing, `try`/`catch` syntax, distributed handler state or new executable kind. Resumable recovery/restarts/effects, new executable kinds, generic control-region callable domains and static signature introspection remain separate future design work. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-B — development + ordinary CI primary-runtime alignment`. Confirm the existing devcontainer already matches the DIST002-A primary GraalVM Community `25i3` / JDK `25.0.4.1` image and exact Maven 3.9.9 binding, replace ordinary Tests CI's Temurin 21 `setup-java` path with a job container using that exact GraalVM image, bootstrap checksum-verified Maven 3.9.9 before repository validation, and verify exact GraalVM/JDK/Maven identity before the full Maven suite. Extend `tools/verify_toolchain.py` with a fail-closed development scope so devcontainer + ordinary-CI drift is now zero while the intentionally remaining Truffle/distribution bindings stay visible for DIST002-C. DIST002-B closes and DIST002-C becomes READY. Project tooling/CI only: no Protos semantics, normative specification, implementation version, native-boundary, license-term, existing public-release artifact, tag, or historical DIST001/PERF evidence change.

- Decompose `TOOL001-F2E2 — verified read-only package-store binding` before implementation now that I024/B009 are closed. Allocate F2E2A as the Protos-only `self:ContentIdentity` canonicalizer/verifier over an already-captured Filesystem; F2E2B as the exact already-selected store-root capture and host custody/handoff boundary; and F2E2C as same-capture integration/final closure. Keep F2E3/F2E4/F2E5 dependency-gated, preserve the prohibition on ambient store scans/package-specific Java tree walking, and defer the concrete captured-backend custody mechanism to B. Documentation/project decomposition only: no production source, normative specification, implementation version, native boundary or license-term change.

- Record explicit project-owner ratification of D033 / specification `0.1.371` under AUD001 on 2026-09-08 after comparative and future-scalability review. Retain the uniform reflective local-slot name domain: standard `hasSlot(name)`, `slotValue(name)`, and `removeSlot(name)` accept exactly semantic `String` values; there is no implicit conversion, stringification, selector coercion, delegation-based String-like acceptance, host-name adaptation, or hidden Unicode normalization. After ordinary argument evaluation, an invalid non-String name fails before local-slot inspection and, for `removeSlot`, before structural mutation. A valid name denotes its exact Unicode scalar-value sequence and the operations remain local-only: `hasSlot` returns canonical false for absence while `slotValue` and `removeSlot` retain their ordinary missing-local-slot Error; a delegated slot does not satisfy them. Preserve implementation freedom to intern or canonicalize storage, cache hashes, use internal Name/Slot IDs, shapes or inline caches when these are unobservable. A future first-class Symbol/Selector/name facility remains a separate design decision and does not silently widen these Core v0.1 APIs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D036 / specification `0.1.374` under AUD001 on 2026-09-08 after comparative, adversarial and scalability review. Retain the general Core-standard Future result-identity rule: unless an operation expressly returns an already-existing Future, every successfully dispatched Future-producing invocation produces a fresh semantic standard Future identity, including immediately resolved, failed or cancelled results. Distinct invocation results therefore remain distinct under `===`, `!==`, `identityHashOf` and `IdentityMap`; implementation-selected pending aliasing or terminal canonicalization may not change that identity. Preserve the separation between Future identity and eventual value/Error/outcome identity, and preserve explicit contracts such as `cancel()`/`detach()` or future memoized/shared-work APIs that deliberately return an existing Future. Physical allocation remains optimizable through elision, virtualization, pooling or shared backing when observable identity and Future-local effects are preserved. This ratifies D036 only; D031 remains subject to its own AUD001 review. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record AUD001 classification of D035 / specification `0.1.373` as `RATIFIED` from recovered explicit project-owner selection on 2026-09-04. Retain the published general Core standard Bytes-producing result contract: unless an operation expressly returns an existing object, every successful operation whose normal result is a `Bytes` object produces its own fresh open standard Bytes identity with independent receiver-owned mutable state, including empty results; results do not alias source/argument Bytes, the producer, another invocation's result, or an implementation-controlled buffer. Preserve the specific `ByteReadable.read` and `Encoding.encode` applications, observable ordinary identity/hash/open-state consequences, failure/cancellation non-exposure of partial results, and implementation freedom for physical sharing such as immutable backing, copy-on-write, slicing or zero-copy when Protos cannot observe collapsed identity or shared mutable state. This records the already-selected/published semantics only. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-A — canonical toolchain authority + drift audit` after explicit project-owner selection on 2026-09-08. Persist root `toolchain.json` as the single repository-owned primary toolchain contract: Java 21 bytecode target, exact GraalVM Community 25.3.4.1 / JDK 25.0.4.1 (`25i3`) primary runtime, Graal/Truffle 25.3.4.1 and Maven 3.9.9, with non-floating upgrades requiring an explicit validated project change. Add a tested `tools/verify_toolchain.py` contract/static-binding auditor so current JDK22/Truffle24 and Temurin21 migration drift is visible before DIST002-B/C align consumers. Preserve historical DIST001/PERF JDK22 evidence and the isolated benchmark IGV analyzer's JDK17 tooling boundary; neither constrains the new primary runtime. DIST002 becomes IN_PROGRESS and DIST002-B becomes READY. Project tooling/governance only: no Protos semantics, normative specification, implementation version, native-boundary, license-term, release tag or existing public-release claim changes.

- Close `I024-D — integrated Filesystem tree-observation conformance and I024/B009 closure`. Add Protos-visible conformance over the production secure NIO tree backend for complete eager exact-name descriptors, regular/directory/link/other no-follow kinds, frozen descriptor state, final-link failure, read-only/source-independent captured Filesystems, captured-subtree verify/use stability and cancellation/late-result custody. Re-run the native-boundary architecture guard, close I024 and B009, and make `TOOL001-F2E2` READY to implement verified read-only package-store binding through the general D046 capability rather than package-specific NIO. Test/documentation closure only: no production source, normative specification, implementation-version or license-term change.

- Record AUD001 classification of D038 / specification `0.1.375` as `RATIFIED` from recovered explicit project-owner selection predating publication. Retain the already-published Encoding semantic-family boundary: Encoding descriptors are semantic Encoding values produced/provisioned only by normative Encoding-producing operations or explicitly permitted host provisioning boundaries; delegation, copying, composition, similarly named slots and structural/protocol compatibility do not confer Encoding membership; Encoding-domain parameters perform no duck typing or implicit coercion; and standard Encoding-family behavior validates the original receiver under the general semantic-family receiver-domain rule after ordinary lookup, with an incompatible receiver failing as the ordinary invalid-receiver `Error` before family-specific computation/state effects and without fallback dispatch. D037 remains a separate AUD001 evidence-recording item; D039 is not included; D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record D037 / specification `0.1.375` as `RATIFIED` under AUD001 from already-recovered explicit project-owner confirmation. Preserve the published Path equality/identity split: portable Path equality is structural and filesystem-independent over rootedness plus the ordered component sequence; Path semantic identity remains ordinary individual object identity, so independently created structurally equal Paths remain distinct through `===`, `!==`, `identityHashOf`, and `IdentityMap`; Path is not added to the closed Core value-identity set; and Filesystem namespace lookup identity, host syntax/normalization, and resource identity remain separate. This patch persists already-recorded owner-confirmation evidence only. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D039 / specification `0.1.376` under AUD001 on 2026-09-08 after comparative and scalability review. Retain the complete published Core v0.1 ActorGroup acquisition contract: `Actor.group(firstMember, additionalMembers...) -> GroupRef` requires one or more explicit ActorRef communication capabilities and validates the complete vector before one synchronous cutover; that cutover creates one fresh Group identity, set-like initial membership and one fresh GroupRef acquisition, with the caller Actor's Process as the Core-created Group lifetime scope. Repeated references to one Actor incarnation do not create weighted membership; creation does not wait for member readiness/reachability, create or rehost members, or grant member lifecycle/control Authority. Group termination does not terminate members, GroupRef reachability does not extend Group lifetime, and GroupRef remains communication-only. Preserve the explicit Core boundary excluding public Group control/membership mutation, Group lookup/reacquisition, registry/service discovery, naming/rebinding, endpoint, placement and transport-selection surfaces; durable/discovery/control layers remain separate future design work. Ratification only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL002-F3D2 — second retained cancelled observation fresh identity`. Extend the dedicated fresh-cancellation Protos evidence with one second same-Process observation of the exact retained `future/cancelled-value-fresh-error.protos` fixture. The inspector captures the first and second signalled Error objects, requires the second occurrence to have immediate parent `Cancelled`, and requires `first !== second`, proving the normative per-observation freshness contract without adding Test-only Future state. Generalize only the existing Java fixture-suite ID guard to admit F3D2. Close F3D and make F3E READY; keep bundled `Runner.protos`, public `runSimple`/`isDExpectation`, the retained main manifest and Java's temporary direct `future-*` ownership unchanged until F4. Test-impact only; no normative specification or implementation-version change.

- Record explicit project-owner ratification of D040 / specification `0.1.377` under AUD001 on 2026-09-08 after comparative and future-scalability review. Retain the published dynamic super-dispatch model: `super` preserves the original receiver and continues lookup after the invocation's `methodHome`; Method remains an invocation role of the single Closure value kind rather than a static category; the complete ordinary argument/spread vector is evaluated before super dispatch; missing `methodHome` signals a fresh `InvalidSuper` with no lookup; a valid home with an empty/exhausted continuation signals ordinary `SlotNotFound`; and no first-class `super`, fallback lookup origin, static Method value kind or P-specific super error is introduced. Record the scalability boundary without changing semantics: `parent(methodHome)` remains the exact Core v0.1 realization under the current single-parent object model, while any future multiple-parent/linearized/directed-resend/next-applicable-method generalization requires a separate explicitly approved specification change. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL002-F3D1 — first retained cancelled observation parent evidence`. Add one Protos-owned live-inspection fixture over the retained `future/cancelled-value-fresh-error.protos` source. The exact retained `observe` Closure is invoked once inside the same fresh Process through the existing generic `executionInspect` boundary, and the caught fresh Error must have immediate parent `Cancelled`. This slice does not make a second observation, does not assert cross-observation freshness, does not modify bundled `Runner.protos`, public `runSimple`/`isDExpectation`, or Java's temporary direct `future-*` corpus ownership. `TOOL002-F3D1` closes, parent F3D becomes IN_PROGRESS and F3D2 becomes READY. Test-impact only; no normative specification or implementation-version change.

- Ratify D043 under AUD001 and publish specification `0.1.385` after explicit project-owner approval on 2026-09-08. Retain the ordinary Closure `ensure(cleanup)` design and clarify only its already-intended boundaries: only a control/Error transfer that escapes cleanup supersedes the pending outcome; an internally handled cleanup Error does not; a selected handler is a consumed one-shot unwind destination that is abandoned if crossed cleanup escapes with a replacement transfer; repeated idempotent `Future.cancel()` calls do not pierce shielding of the already-honored request; and exactly-once cleanup is a semantic-unwind guarantee, not fail-stop/distributed crash recovery. Preserve task-local dynamic control and defer resumable/multi-shot continuations, hard termination, durable/distributed compensation and richer diagnostic aggregation to separate future designs. Specification/project documentation only: no runtime/source, implementation-version, native-boundary or license-term change.

- Close `I024-C — secure NIO tree observation + immutable captured backing`. Extend the existing complete-tree `ProtosNioReadOnlyTreeFilesystemBackend` with D046 `entries` and `captureTree`: every selected directory is bound through fresh relative `SecureDirectoryStream` handles with `NOFOLLOW_LINKS`, direct-child classification uses no-follow basic attributes and exact representable names, recursive capture never follows child links, and regular bytes stream into private implementation-managed temporary blob backing rather than accumulating the complete payload in heap. Captured Filesystem backends keep immutable name/kind/tree metadata, share backing across captured subtrees, retain backing while opened Files exist, and reclaim it through internal Cleaner/lease custody without adding a public Filesystem `close`. Preserve existing read-only open behavior and all source/captured authority boundaries. I024-C closes, I024-D becomes READY, and B009 remains READY until integrated Protos conformance closes I024. No normative specification or native-Closure-boundary change.

## 0.2.252-SNAPSHOT

- Record explicit project-owner ratification of D041 / specification `0.1.378` under AUD001 on 2026-09-08 in its effective form after the already-ratified D042 / `0.1.379` correction. Retain D041's still-effective Filesystem namespace-mutation contract: one confined Filesystem authority for both paths; indivisible failure-atomic `replace`/`remove` namespace transitions; no copy-then-delete or truncate-and-write substitute; same-resource replacement as a no-op; stable already-open File binding; pre-commit cancellation/failure with no operation-attributable namespace mutation; irreversible committed success that cannot later surface as failed/cancelled; fail-closed backends when the required determinate transition cannot be provided; no implicit Filesystem FIFO; and explicit separation of live atomic visibility from crash durability, including that File `sync()` is not a namespace-durability barrier. Explicitly exclude D041's historical ordinary-file-only final-entry restriction from this ratification because D042 superseded it with the race-safe final-entry namespace-selection rule. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL002-F3C2 — stored observation negative policy`. Strengthen the already-private bundled-Protos `stored:Error` inspector so a positive observation requires both exact caught-versus-stored Error identity and immediate `Error` parentage. Add three Protos-owned C2 fixtures: malformed `stored` policy fails closed before inspector execution; a correct `stored:Error` CaseSpec with a different observed Error is an inert expectation mismatch; and exact stored identity with immediate `Cancelled` parent is an inert parent mismatch. Generalize only the existing Java fixture harness id guard to admit C2 evidence. Close F3C, make F3D/F3D1 READY, keep public `runSimple`/`isDExpectation` unchanged and retain Java as temporary direct `future-*` owner until F4. No normative specification, native-boundary or license-term change; implementation version becomes `0.2.252-SNAPSHOT`.

## 0.2.251-SNAPSHOT

- Record explicit project-owner ratification of D042 / specification `0.1.379` under AUD001 on 2026-09-08 after comparative review against POSIX/Unix, Java NIO, Rust, Go, Python and .NET filesystem models. Retain the published race-safe namespace-entry correction: final Path components are selected as namespace entries without following final symbolic-link/reparse/other indirection and without a separate mutable file-kind preclassification; `replace`/`remove` may act on entry kinds when the backend can provide the required atomic transition; unsupported atomic entry-kind or source/target-kind combinations fail as `IOError` rather than being emulated through check-then-act; and `remove` remains non-recursive. This ratifies D042 only: D041's separate atomicity, commitment, cancellation, stable-open-File and namespace-durability package remains subject to its own AUD001 review. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `I024-B — standard Filesystem tree-observation materialization`. Extend the existing host-provisioned `ProtosStandardFilesystemProtocol` shared operation bridge with D046/0.1.384 `entries` and `captureTree` selectors without adding another native-Closure construction site. Existing backends remain source-compatible through default-fail tree-observation methods until I024-C. Successful `entries` materializes one fresh standard Array containing fresh frozen root-Object descriptors with exactly semantic String `name`/`kind`; successful capture materializes a fresh standard Filesystem through an internal read-only captured-backend contract, structurally rejecting mutation and write/create/truncate/append opens and exposing no Filesystem `close`. Add Java bridge coverage plus Protos-source conformance proving that both selectors dispatch through ordinary Futures and that unsupported current NIO backends fail as `IOError`. I024-B closes, I024-C becomes READY, and B009 remains READY. No normative specification change; implementation version becomes `0.2.251-SNAPSHOT`; the audited Core native boundary remains 113 construction sites across 30 providers.

## 0.2.250-SNAPSHOT

- Reconcile AUD001 after the concurrent D045 ratification publication. Remove three stale current-state references that still treated D045 as unratified: the priority triage now covers D039-D044 plus D020, the active backwards-review procedure continues from D044, and the separately ratified specification `0.1.90` child-outcome policy now describes D045's ownership-scope core as separately ratified. Preserve D045 as `RATIFIED`, AUD001 as `OPEN`, D046 outside AUD001, and all historical changelog entries unchanged. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL002-F3C1B4`, parent `TOOL002-F3C1B`, and parent `TOOL002-F3C1` by reconciling the published B1 classifier, B2 retained live-inspection evidence, and B3 bundled-Protos evaluator composition. Positive `future-observation-error-identity / stored:Error` policy is complete inside C1 without activating public `runSimple`, changing Java's temporary `future-*` ownership, adding fresh mode or stored negatives. `TOOL002-F3C2` becomes READY; F3D/F3E/F4 remain dependency-gated. Documentation/governance only: no executable, specification, implementation-version, native-boundary or license-term change.

- Record explicit project-owner ratification of D045's task-scoped structured-ownership core under AUD001 on 2026-09-08. Retain the published semantics that synchronous Closure/method activations do not create structured scopes; returning, storing or wrapping a pending Future does not alter its ownership edge; distinct asynchronous child tasks own their own descendants; and `detach()` is the explicit operation that removes the edge. This ratifies D045 without changing normative specification or implementation and remains independent of the already-ratified specification `0.1.90` structured-child terminal-outcome policy. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of the specification `0.1.90` structured-child terminal-outcome policy after AUD001 provenance reconstruction, prior-art comparison and adversarial review. Retain the published Core v0.1 separation between structured lifetime/cleanup and explicit Future outcome observation: child failure/cancellation does not automatically fail/cancel its normally completing owner or siblings, while owner failure/cancellation still cancels non-detached children and waits for cleanup; add no hidden failure-consumption state. Close `STRUCTURED_CHILD_OUTCOME_FOLLOWUP` independently of D045, whose ownership-scope core remains `NEEDS_USER_DECISION`. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record the AUD001 D045 review without ratifying or changing language semantics. Recommend retaining D045's task-scoped ownership core while leaving D045 as `NEEDS_USER_DECISION`; separate the inherited specification `0.1.90` non-propagating structured-child outcome policy into an OPEN AUD001 follow-up covering failure propagation, aggregation, sibling cancellation, supervision and owner termination. D046 remains excluded. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `I024-A2 — post-D046/0.1.384 compatibility re-audit` without production modification. Re-audit the published host-neutral tree-observation substrate against the explicitly approved eager-Array, Filesystem-not-Directory and non-Closable captured-Filesystem decisions: complete `List<Entry>` snapshotting remains compatible with eager `entries`, duplicate exact names fail closed, `CapturedTree` remains internal custody only, successful capture transfer does not invoke `releaseIfUntransferred`, and cancellation/late-result/materialization-failure paths release only untransferred custody. Confirm the standard Filesystem surface still has no `close`; record the pre-existing Java `AutoCloseable` read-only tree backend as an I024-C implementation-custody constraint rather than public lifecycle. Focal `ProtosFilesystemTreeObservationFlowTest` and the complete test suite are required before publication. I024-B becomes READY; B009 remains READY. No source, test, normative specification, native-boundary, implementation-version or license-term change.

- Publish the explicitly approved D046 re-evaluation as specification revision `0.1.384`. Retain eager `Filesystem.entries(path) -> Future<Array>` for Core v0.1 while explicitly deferring a future incremental facility; reaffirm `Filesystem` rather than a new public Directory/DirectoryEntry authority family; and clarify that a successful captured Filesystem is immutable/read-only but is not made a standard `Closable` receiver and imposes no programmer-managed release obligation. Preserve exact-name/no-follow-kind/unique-result, unspecified-order, non-point-in-time capture and capture-verify-use semantics. Add I024-A2 as the required post-reevaluation compatibility audit and block I024-B on it; B009 remains READY. Specification/design/project-state only: no runtime/source, implementation-version, native-boundary or license-term change.

- Open AUD001 — Retrospective design-decision ratification audit as HIGH-priority non-normative governance work covering D001-D045 while explicitly excluding separately reviewed D046. Record D037/D038 as already owner-confirmed; prioritize D045-D039 and D020 where recovered evidence does not yet demonstrate explicit owner selection; require provenance/substance review for D021-D036 and evidence rather than assumption for D001-D019. Patch execution and publication are not ratification. AUD001 authorizes no automatic reopening, normative specification change, implementation change, blocker transition, or replacement decision.

- Publish `TOOL002-F3C1B3 — stored observation evaluator composition`. Bundled `Runner.protos` composes the already-closed exact `future-observation-error-identity / stored:Error` classifier with one positive same-Process `executionInspect`-compatible inspector: the inspector reads only retained local `error`/`observe`, invokes `observe()` once, and returns inert Boolean evidence for exact caught-versus-stored Error identity. A Protos-owned fixture passes the retained CaseSpec, retained source and bootstrap-local `executionInspect` through the evaluator. `isDExpectation`, public `runSimple`, fresh mode, stored negatives and canonical TOOL002 ledgers remain unchanged until their later slices. No normative specification or Java host-mechanism change.

## 0.2.249-SNAPSHOT

- Add an explicit user design-approval gate and replace generated patch launchers' clean-caller-checkout requirement with isolated local worktree publication. Agents must research, compare, falsify, and recommend substantive design choices without self-selecting or closing them; publication launchers must build/validate on a temporary local branch rooted at current `origin/main`, rebase/revalidate on concurrent advancement, fast-forward push only the validated commit to `main`, and remove their temporary local worktree/branch on success or failure while leaving caller state untouched. Governance/documentation only: no normative Protos semantic, implementation-version, runtime, native-boundary, or license-term change.

- Close `DOC001-L — Process, I/O, Filesystem/File capabilities, and authority`. Publish `docs/guide/11-process-io-filesystems-and-authority.md` with the programmer-facing authority model for bootstrap-local Process and optional Filesystem capabilities, canonical args/environment snapshots, independently optional byte standard streams with explicit Encoding, explicit borrowing/owning TextReader/TextWriter composition, ordinary-Future I/O with commitment/cancellation and lifecycle cutovers, bounded byte read/write ordering/backpressure, structural authority-free Path values, explicit Filesystem open configuration, stable File resource binding/capability shape, positioned/append/durability distinctions, namespace replace/remove, and resource ownership. Explicitly record that D046 `Filesystem.entries`/`captureTree` are normative at spec 0.1.383 but not yet current runnable reference behavior while I024 remains IN_PROGRESS. Cross-link the Path tutorial plus existing bundled-tool Protos consumers and current I013-I017/I021 implementation evidence. Documentation-only: no runtime, test, library, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-K — Actors and Actor Groups`. Publish `docs/guide/10-actors-actorrefs-and-groups.md` with the programmer-facing model for Actor-private mutable state and serialized execution, module-backed `Actor.spawn`, explicit Actor snapshot/capability transfer, incarnation-bound ActorRef identity, send/request/SendOperation backpressure, concrete acceptance and uncertainty/no-transparent-replay rules, Error/control isolation, graceful `stop` plus independent known-termination Futures, and fixed Core failure policy. Explain `Actor.group(...)`, stable Group identity independent of membership, communication-only GroupRef identity/transfer, one-eligible-member routing, pre-acceptance reselection, Group/Process lifetime separation, and the absence of Core broadcast/discovery/post-creation membership-control authority. Cross-link the executable `10-actors` and `11-actor-groups` tutorials plus current I011/LM005 evidence and advance DOC001-L. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-J — Isolated parallel execution`. Publish `docs/guide/09-isolated-parallel-execution.md` with the programmer-facing model for ordinary Closure `parallel(...)`, the absence of a public P/worker object, P-local Closure projection without caller lexical/receiver/control capture, explicit argument snapshot/transfer and `NonParallelValue` authority rejection, Future/structured-ownership composition, deterministic Array `parallelMap`/`parallelFilter`/`parallelFindIndex`/canonical `parallelReduce`/stable `parallelSort`, and exclusive Bytes/ByteRegion `parallelRange` writable partitioning. Reconcile the stale detailed DOC001 E-I rows discovered during the current-main status audit while closing J and advancing K. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DIST001 — End-user distribution and release engineering` after the explicitly authorized direct publication of `Protos 0.2.236`. Reconcile D4-D6, E4, E5 and E6 from observed public evidence: lightweight tag `v0.2.236` resolves exactly to candidate `957b1e16793a682de1d6406e37b5734c44d32d19`; the GitHub Release is a non-draft pre-release; final candidate Maven and envelope verification passed; and re-downloaded `protos-0.2.236-posix-jvm.zip`, its basename-only checksum and `RELEASE_MANIFEST.txt` match the frozen SHA-256 identities. Persist the publication record while leaving later main documentation/implementation work independent of the 0.2.236 release. Documentation/project-state only; no runtime, specification, implementation-version or license change.
- Close `DOC001-I — Futures and structured concurrency`. Publish `docs/guide/08-futures-and-structured-concurrency.md` with the programmer-facing model for Closure `future()` execution, fresh Future identity, pending/resolved/failed/cancelled state, `value()` suspension and Error observation, Future adoption/flattening, asynchronous `then` continuations, deterministic `Future.all`, the intentional absence of generic scheduler-timing race/select, cooperative cancellation boundaries, task-scoped structured ownership, non-propagating child outcomes, and `detach()` as ownership-edge removal rather than Actor-independent lifetime. Cross-link the executable `09-futures` tutorial and retained Future conformance evidence and reconcile DOC001 navigation/status. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-H — Errors, handlers, ensure, and resource lifetime`. Publish `docs/guide/07-errors-handlers-ensure-and-resource-lifetime.md` with the programmer-facing model for ordinary Error objects and fresh standard failure occurrences, exact-object non-resumable `signal()`, delegation-based dynamic `Error.handle`, innermost selection and selected-frame deactivation, task-local handler scope, standard Closure `ensure`, exact-once cleanup across normal/return/Error/cancellation exits and suspension, later-cleanup-transfer precedence, cancellation-safe cleanup, explicit asynchronous release, and the absence of deterministic GC finalization. Cross-link retained handler/ensure conformance plus ordinary-Protos filesystem/package-tool usage and reconcile DOC001 navigation/status. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-G — Modules and imports`. Publish `docs/guide/06-modules-and-imports.md` with the programmer-facing model for moduleContext top-level state, ordinary eager `import(...)`, semantic-String specifier validation, specifier/ModuleKey/module-instance separation, canonical-key identity, Actor-local caches, cache-before-execute cyclic initialization, partial module visibility, failed-initialization eviction/retry, initial-module identity, and the boundary between Core module lifecycle and resolver policies such as `std:`/`self:`. Cross-link current Standard Library/package-tool import examples, module runtime evidence, and reconcile DOC001 navigation/status. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DIST001-E4D3 — candidate claims/blockers/spec audit`. Independently re-audit the exact frozen 4-capability/5-limitation claim set SHA-256 `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33` against detached candidate `957b1e16793a682de1d6406e37b5734c44d32d19`, never moving main: require candidate parent `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` and only `pom.xml` candidate delta; parse B001-B008 independently as CLOSED; require newest global spec revision `0.1.382`, D043/D044/D045 and relevant v0.1 Draft owners; and verify candidate-time portable CLI/Core/control/concurrency/standard-facility capabilities plus experimental/runtime/tooling/guide limitations. Persist `d3_claims_blockers_spec=PASS` and make E4D4 READY. No B5 repetition, Maven run, tag/Release collision check, runtime/spec/implementation/license change or publication authorization occurs.
- Start `I024 — Filesystem directory observation + captured-tree capability` with `I024-A`. Add host-neutral `ProtosFilesystemTreeObservationFlow` for D046 without publishing `Filesystem.entries` or `captureTree`: non-Path arguments fail before backend authority, each invocation owns an independent Future/Actor/cancellation lifecycle, child observations are exact-name/no-follow-kind DTOs copied defensively with duplicate-name rejection, and backend/materialization failures become ordinary `IOError`.
- Model a successful captured tree as opaque host-neutral custody plus a release callback. Cancellation, materialization failure, or another terminal result that wins before transfer releases custody; successful transfer performs one atomic producer-side commit/resolve cutover and adds no source-side effect or Filesystem-wide sequencing.
- Subdivide I024 into A host-neutral flow, B standard Filesystem public materialization, C secure NIO + immutable captured backend, and D integrated Protos conformance/B009 closure. B009 remains READY and TOOL001-F2E2 remains dependency-blocked on I024. No specification or native-boundary change.

## 0.2.248-SNAPSHOT

- Close `DOC001-F — Values, identity, equality, and collections`. Publish `docs/guide/05-values-identity-equality-and-collections.md` with the programmer-facing model for canonical null/Booleans, the closed Number/String/singleton value-identity set, non-overridable `===` versus customizable strict-Boolean `==`, hashing, indexed `at`/`atPut` syntax distinct from slots, Core Array/Map/IdentityMap/Bytes semantics, snapshot iteration, and the Core-versus-Standard-Library boundary for `std:collections/Array`, `Set`, and `IdentitySet`. Cross-link the runnable Map and IdentityMap tutorials plus ordinary-Protos collection modules and reconcile DOC001 navigation/status. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-E — Control flow through ordinary protocols`. Publish `docs/guide/04-control-flow-through-protocols.md` with the programmer-facing model for lazy Boolean `ifTrue`/`ifFalse`/`and`/`or`, ordinary trailing-Closure arguments, standard Closure-specific `Object.while`, strict canonical Boolean loop decisions, ignored body values, normal `null` completion, ordinary lookup/shadowing, Error/non-local-return transfer, suspension replay, and the body-Future boundary. Cross-link representative executable conformance evidence and reconcile guide navigation plus DOC001/I023/B007 project ledgers. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Open `DIST002 — Development/release toolchain alignment` as deferred `OPEN` release/build-environment work. Record the current intentional Java 21 bytecode target but accidental primary-runtime split between the GraalVM JDK25 devcontainer and DIST001's frozen GraalVM JDK22/Truffle24 release contract. Require one authoritative toolchain coordinate source, aligned devcontainer/ordinary CI/release validation, explicit runtime migrations, and pre-provisioned primary runtimes so normal validation gates do not download JDKs on demand. DIST002 is explicitly not a dependency of the already-frozen 0.2.236 candidate and starts after DIST001. Documentation/project-state only; no runtime/specification/version/license/publication change.

- Publish D046 / specification revision `0.1.383` to resolve the normative part of B009 with general capability-confined `Filesystem.entries(path)` and `Filesystem.captureTree(path)`. Entries expose exact no-follow child name/kind descriptors; captureTree returns a fresh immutable read-only captured Filesystem. The capture need not represent one atomic source instant: verify-then-use hashes and consumes the same captured capability, avoiding source-path TOCTOU without a package-specific Java tree walker. B009 moves BLOCKED -> READY, I024 becomes READY, and TOOL001-F2E2 moves to BLOCKED_BY_DEPENDENCIES on I024. No runtime/source, implementation version, license term or native-boundary change.
- Close `DIST001-E4D2 — extracted release-aware B5 candidate gate`. Run the already-published E3C1 B5 surface once against exact detached candidate `957b1e16793a682de1d6406e37b5734c44d32d19` and immutable `protos-0.2.236-posix-jvm.zip` SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` in explicit public-prerelease / clean-source mode. Require B5 archive identity, outside-checkout Package Tool, bundled Test Tool and exact optimizing GraalVM JDK22/Truffle24 checks to pass without modifying the archive. Persist `d2_release_b5=PASS` and make E4D3 READY. D3 retains claim/blocker/spec truth audit and D4 retains tag/Release collision/publication guards. No repository executable, implementation/spec/runtime/license contract, tag, GitHub Release, asset upload or release-publication authorization changes.
- Open `PERF004 — Cross-language runtime performance characterization` as a deferred `OPEN` performance work item. Plan A correctness-first Python/JavaScript/Java algorithm-equivalent baseline, B attribution only for material measured gaps, and C a ranked 3–5 item optimization-priority output. Opening PERF004 does not start measurements or authorize a Protos optimization; it reuses PERF001 methodology and PERF003's separation of compiler diagnostics from actual timing. Documentation/project-state only; no runtime, specification, implementation-version, native-boundary, or license-term change.


- Close `TOOL001-F2E1C` and parent `TOOL001-F2E1` without production or implementation-version change. Publish fixed `protos-package-tree-v1` conformance streams/digests cross-computed by two one-off Python oracles and verified by a separate JDK-only Java test oracle, covering ordering, binary/empty content, varuint boundaries including >64-bit, framing, exact case, mutations, invalid paths/reserved names/case collisions, special-entry rejection, and empty-directory/metadata/store-layout non-semantics. Reconcile the older identity audit so canonicalization is no longer described as open. The required post-E1 audit confirms Core Filesystem still lacks portable confined directory enumeration/entry-kind/stable-snapshot semantics; allocate B009 and mark F2E2 BLOCKED rather than adding a package-specific Java tree walker. No normative specification or native-boundary change.
- Publish `TOOL002-F3C1B2A4P2 — standard Object.slotValue reflection prerequisite`. The F3C1B2 retained-inspector progression reached local-slot value reading and exposed that the already-normative `Object.slotValue(name)` selector remained absent. Implement only that general Core operation: require one semantic String, read only an ordinary receiver's local binding through `readLocalSlot`, return the exact stored value, and signal ordinary Error when the receiver/name has no such local binding.
- Add Protos conformance for exact stored identity, no delegation, invalid name and represented-value absence. Reconcile the native-boundary inventory from Object 6 to 7 sites and Core 112 to 113 sites. `slotNames` and `removeSlot` remain out of scope; Runner, `executionInspect`, Future policy, canonical TOOL002 ledgers and specification are unchanged.

## 0.2.247-SNAPSHOT

- Close `DIST001-E4D1 — envelope/audit/record consistency`. Independently cross-check the frozen selection, candidate artifact, C3 claims, C4 envelope and C5 audit against candidate `957b1e16793a682de1d6406e37b5734c44d32d19` and verify exact candidate-local archive/envelope/audit bytes: archive `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`, claims `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33`, notes `98684922feebb1cec41da2a51fca6776cd76aa5c99561b37adb733e5b944ed36`, manifest `2d71e48bf27e52d76bd4bd9166dca4298487532b1de08e832758c7f2abe7dcd4`, checksum file `34b2d9a86c0e136ac2f9d92c8edf94563b9ea941c896f54eddce929680969035` and audit `0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b`. Require clean detached candidate state, exact E3B three-file envelope, basename-only portable checksum and byte-identical main/local audit. Persist the D1 PASS/PENDING D2-D6 validation ledger and make E4D2 READY. No B5 execution, claim-truth re-audit, tag/Release collision check, Maven run, implementation/spec/runtime/license change or publication authorization occurs.
- Close `DIST001-E4C5 — candidate-audit materialization` and parent E4C. Materialize the exact E3C3 audit for candidate `957b1e16793a682de1d6406e37b5734c44d32d19` with explicit candidate selection authorization, specification `0.1.382`, PASS capability/limitation/blocker reviews and `release_publication_authorized=false`; retain byte-identical main and candidate-local audit copies with SHA-256 `0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b` and validate them using the published E3C3 audit verifier. Decompose E4D into D1-D6 and make only D1 READY. No full B5 candidate gate, tag, GitHub Release, asset upload, implementation/spec/runtime/license change, or publication authorization occurs in this slice.
- Close `PERF003 — Collection algorithm Truffle compilability` by reconciling retained A4g/A4h/A4i evidence. Exact result `528` remains correct; the stable control is `50681:150026:150000`; sync/task splitting and preparation-only boundary are rejected as sufficient fixes; preparation-only timing is `0.578142x` control despite retaining the bailout. Retire the zero-bailout gate for PERF003 rather than publish a broad opaque boundary or continue threshold-shaving edits. No runtime, specification, implementation-version, native-boundary, or license-term change.


- Close documentation-only `TOOL001-F2E1B — canonical byte stream + method/hash contract`. Serialize the E1A finite path->bytes map in exact ASCII path-byte order as one domain-separated binary stream: `protos-package-tree-v1` + NUL magic, tagged FILE records, canonical minimal arbitrary-precision unsigned base-128 path/content lengths, exact path/content bytes, and one END tag. Empty files are explicit zero-length contents; directories/metadata/per-file hashes remain absent. Define current ContentIdentity generation/verification as `protos-package-tree-v1 sha256:<64-lowercase-hex>`, where sha256 is standard SHA-256 over the complete stream; method and algorithm tokens remain orthogonal for future evolution. E1C independent vectors/closure becomes READY. No source, specification, implementation-version or native-boundary change.
- Close `DIST001-E4C4 — deterministic release-envelope generation`. Consume the exact frozen E4C3 claim record SHA-256 `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33` and independently verified `protos-0.2.236-posix-jvm.zip` / `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` through the existing E3B metadata generator. Require two byte-identical generations, retain one candidate-local `release-envelope-0.2.236` envelope, preserve all four capability and five limitation bullets exactly/in order, and pass the existing E3C2 independent envelope verifier. Persist notes `98684922feebb1cec41da2a51fca6776cd76aa5c99561b37adb733e5b944ed36`, manifest `2d71e48bf27e52d76bd4bd9166dca4298487532b1de08e832758c7f2abe7dcd4` and checksum-file `34b2d9a86c0e136ac2f9d92c8edf94563b9ea941c896f54eddce929680969035` identities; mark envelope generated and make E4C5 READY. No candidate audit, tag, GitHub Release, asset upload or release-publication authorization occurs.
- Publish `TOOL002-F3C1B2A1P1 — standard Object.hasSlot reflection prerequisite`. The F3C1B2 live-inspector audit exposed that the already-normative inherited `Object.hasSlot(name)` selector was absent from Core. Implement exactly that general reflection operation as one reviewed `ProtosStandardObjectProtocol` representation bridge: require one semantic String argument, inspect only the receiver's own local slot table, return canonical true/false, and treat opaque represented values as having no representation-owned local slots. Non-String or invalid arity signals the ordinary Error.
- Add Protos conformance for local-only/non-delegating presence, represented-value false, invalid-name and arity failure. Reconcile the executable/native architecture inventory from 111 to 112 Core construction sites and Object provider 5 to 6. `slotNames`, `slotValue`, and `removeSlot` remain out of scope; Test Tool Runner, `executionInspect`, Future policy and canonical TOOL002 ledgers are unchanged. No normative specification change.

## 0.2.246-SNAPSHOT


- Subdivide `TOOL001-F2E1 — protos-package-tree-v1 ContentIdentity` into E1A logical-tree/path domain, E1B canonical byte stream + method/hash, and E1C independent vectors/closure; close documentation-only E1A. The v1 logical tree is the already-materialized package payload, not an arbitrary checkout: every valid regular file contributes its exact path+bytes; root `protos.toml` is mandatory; directories are structural and empty directories non-semantic; symlinks/special entries are rejected; modes/timestamps/ownership/xattrs and other host metadata are excluded. Freeze a conservative ASCII artifact-path domain with slash-only separation, `.`/`..` and Windows-reserved rejection, and ASCII case-fold collision rejection. Source packaging/include-exclude policy remains separate from ContentIdentity and no `.gitignore`/ambient VCS policy is consulted by the identity method. E1B becomes READY; no source, specification, implementation-version or native-boundary change.
- Close `DIST001-E4C3 — release-note claims selection`. Freeze an ordered candidate-specific capability/limitation record for `957b1e16793a682de1d6406e37b5734c44d32d19` / `protos-0.2.236-posix-jvm.zip` SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` and specification `0.1.382`. Positive claims stay within the verified portable CLI, Core object/control model, Future/parallel/Actor concurrency, and implemented standard value/I/O facilities. Limitations explicitly disclose experimental draft status, exact POSIX/GraalVM JDK22 + Truffle24 runtime scope with no bundled JDK, incomplete Package Tool/Test Tool surfaces, and incomplete Programming Guide coverage. Candidate B001-B008 blocker review passes. E4C4 becomes READY to render the deterministic envelope without changing these claims; no tag, GitHub Release, release asset or publication authorization occurs.

- Allocate `TOOL001-F2E — external immutable-package execution` after closed `TOOL001-F2D`. Close documentation-only `F2E0` with the fresh prerequisite audit and E1-E5 decomposition. The next READY work is the versioned `protos-package-tree-v1` ContentIdentity canonical tree contract; store binding, execution-plan extension, host resolver and public-run integration remain dependency-gated. Current Core Filesystem still lacks the broad portable tree observation needed by external package verification, so this checkpoint forbids a package-specific Java tree-walker escape hatch. Fetch/network remains separate from already-materialized normal execution. No implementation version, source, normative specification or native-boundary change.
- Close `TOOL001-F2D3C3B — public workspace run`, parent C3/C/F2D3 and the bounded workspace-only `TOOL001-F2D`. Publish `protos run <entry> [args...]`: the current working directory is the project root, `<entry>` is an explicit portable logical module in the root package, and neither the command nor entry is included in `process.args()`. No implicit `Main`, manifest entry field, filesystem-path interpretation or fresh dependency selection is introduced.
- Route the public command only through the closed C3A driver, therefore preserving C1 read-only Package Tool preflight, detached-plan handoff, exact self:/dep:/std: package resolution and the separately-authorized C2 application Process. The CLI supplies the existing host environment/stdio authority with exact UTF8 stream bindings and no default application Filesystem.
- Translate completed/failed/cancelled workspace outcomes into ordinary CLI exit behavior and report preflight/lock/source/entry failures as `protos run: ...`. Add real-Protos CLI integration plus C3A/C2/C1/resolver/full-suite regressions and README/help documentation. External registry/Git materialization remains later TOOL001-F2 work; no normative specification or native-boundary change occurs.

## 0.2.245-SNAPSHOT

- Close `DIST001-E4C2 — archive identity + SOURCE/RUNTIME verification`. Add an independent non-regenerating verifier for persisted `protos-0.2.236-posix-jvm.zip` / `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`. It cross-checks the E4 selection/artifact records, exact candidate worktree `957b1e16793a682de1d6406e37b5734c44d32d19`, external SHA-256, single-root ZIP/CRC, exact public-prerelease SOURCE identity, GraalVM Community JDK22 + Truffle 24.0.0 runtime metadata, full internal SHA256SUMS coverage/values, and the shaded JAR `Implementation-Version: 0.2.236`. Persist independent verification=true and make E4C3 READY. No archive regeneration, release claims, envelope, audit, tag, GitHub Release or asset publication occurs.
- Close `DIST001-E4C1 — public-prerelease portable ZIP build`. From the already-selected clean detached candidate `957b1e16793a682de1d6406e37b5734c44d32d19`, run the existing `dist/build_portable.py --public-prerelease --release-baseline 3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` path to produce exact archive `protos-0.2.236-posix-jvm.zip` with SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`. Persist that minimal artifact identity in `docs/project/DIST001_E4_CANDIDATE_ARTIFACT.txt`; E4C2 becomes READY and retains ownership of independent archive/SOURCE/RUNTIME verification. No release envelope, candidate audit, tag, GitHub Release or release-asset publication is created; release publication remains explicitly unauthorized.
- Subdivide `TOOL001-F2D3C3` into C3A CLI-neutral workspace-run driver composition and C3B public `protos run` policy/wiring plus final F2D3/F2D closure; close C3A only.
- Add `ProtosWorkspaceRunDriver`, a host-only mechanical composition boundary whose explicit request supplies selected Core/Package-Tool/project roots, Standard Library resolver, root-package logical entry, and application bootstrap authority. It executes the closed C1 read-only preflight, passes only the detached `ProtosPackageExecutionPlan` into the closed C2B application boundary, and returns only `ProtosExecutionOutcome`.
- Add focused real-workspace coverage proving exact dependency-backed execution through the new driver while `protos.toml` and `protos.lock` remain byte-identical. No CLI syntax/current-directory/default-entry policy, normative specification or native boundary changes occur; C3B becomes READY.

## 0.2.244-SNAPSHOT

- Close `DIST001-E4B4 — release-only candidate lineage verification` and parent E4B. Add an independent Git-object verifier and fixture coverage that reconstruct `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` -> `957b1e16793a682de1d6406e37b5734c44d32d19` without using materialization helpers: exactly one parent, only modified `pom.xml`, byte-exact root `0.2.236-SNAPSHOT` -> `0.2.236` transition, one clean detached registered candidate worktree, no local/remote candidate refs, future tag `v0.2.236` absent locally and on origin, and release-publication flags still false. Decompose E4C into C1-C5 and make only E4C1 READY. No release archive, envelope, tag, GitHub Release or asset is created by this slice.

- Close `TOOL001-F2D3C2C — C1->C2 authority-isolation integration` and parent `TOOL001-F2D3C2` without production changes. The real integration builds a detached workspace execution plan in the separately terminated Package Tool Process, executes package-backed source in a distinct C2B application Process, proves locked dependency routing from the detached DTO, and verifies that the C1-only `projectTreeFilesystem` capability is not visible to application source. `TOOL001-F2D3C3` becomes READY.
- Close `DIST001-E4B3B2 — real candidate creation + SHA persistence`. Materialize the first real detached public-prerelease candidate `957b1e16793a682de1d6406e37b5734c44d32d19` from frozen baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` by composing the published B1/B2/B3A mechanisms: only the root Maven project identity moves from `0.2.236-SNAPSHOT` to `0.2.236`. Validate the candidate before persisting its exact SHA in `DIST001_E4_SELECTION.txt`; keep the detached worktree as the temporary local reachability anchor. Close E4B3B/E4B3 and make E4B4 READY for independent release-only lineage verification. Release publication remains explicitly unauthorized; no branch, tag, GitHub Release or release asset is created, and active `main` remains on its ordinary SNAPSHOT development line.
- Close `TOOL001-F2D3C2B — detached plan -> fresh application Process wiring`. Add `ProtosWorkspacePackageApplicationExecution`: reconstruct the closed F2D3B resolver only from the selected project root plus immutable execution-plan DTO, resolve an explicit root-package entry, bootstrap a fresh application Process from copied arguments/environment and explicitly supplied standard byte streams/Encoding binding names, execute the canonical entry through C2A, and terminate the Process before returning its inert outcome.
- Keep C2B authority-neutral with respect to the Package Tool: it accepts no tool Process, activation, Filesystem or mutable Protos plan, grants no default Filesystem, and performs exact source-backed Encoding binding lookup without case folding or implicit defaults. C1->C2 authority isolation remains C2C.
- Add real-Protos focal coverage for application args/environment plus stdout Future suspension/resume and exact Encoding selection, along with C2A/resolver regressions. No normative specification or native boundary changes occur. `TOOL001-F2D3C2C` becomes READY.

## 0.2.243-SNAPSHOT

- Subdivide `TOOL001-F2D3C2` into C2A canonical initial-module execution, C2B detached-plan application Process wiring, and C2C C1->C2 authority-isolation integration/closure; close C2A only.
- Add package-neutral `ProtosCanonicalInitialModuleExecution` with cache-before-execute canonical identity, RootTask execution, READY transition and failed/cancelled cache eviction. No normative specification or native-boundary change.

## 0.2.242-SNAPSHOT

- Publish `TOOL002-F3C1B1 — stored observation expectation recognition`. Bundled `Runner.protos` adds only `isFutureStoredObservationExpectation(spec)`, recognizing exactly `future-observation-error-identity / stored:Error`. A Protos-owned fixture proves exact recognition and rejection of `fresh:Cancelled`, a wrong expected token, and another Future kind.
- Keep B1 classification-only: no `executionInspect`, no `observe()` call, no evaluator, no `runSimple` activation, no fresh-mode policy, and no canonical ledger change until B4. No normative specification or host production change.

## 0.2.241-SNAPSHOT

- Close `DIST001-E4B3B1 — candidate materialization composition + recovery/idempotency guard` and subdivide B3B before creating the real candidate. Add `dist/materialize_release_candidate.py` plus isolated Git fixtures. The helper composes E4B1 -> E4B2 -> E4B3A, safely resumes exact B1/B2 intermediate states, reuses an already-created exact detached candidate, rejects broader dirty/ref states, removes only incomplete worktrees it created itself, and keeps a completed detached worktree registered as temporary commit reachability. E4B3B2 becomes READY for the first real `0.2.236` candidate + SHA persistence. The frozen selection remains UNMATERIALIZED and publication unauthorized; no real candidate, branch, tag, GitHub Release, asset, implementation, specification, runtime, workflow or license-term change occurs in this slice.
- Close `DIST001-E4B3A — candidate-commit primitive + guards` and subdivide E4B3 into B3A commit mechanics and B3B real candidate materialization. Add `dist/commit_release_candidate.py` plus isolated Git-worktree fixtures. The helper accepts only the exact unstaged E4B2 POM transition at the selected baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`, stages only `pom.xml`, suppresses local hooks/signing for the mechanical commit, and verifies the new detached commit has exactly the selected baseline as parent, changes only the exact public-version POM, leaves the worktree clean and does not create/move local branch or tag refs. E4B3B becomes READY. No real release worktree/candidate SHA, branch, tag, GitHub Release, asset, implementation, specification, runtime, workflow or license-term change occurs in this slice.
- Publish `TOOL002-F3C1A — live-result inspection host mechanism`. Extend the existing exact captured fresh-Process tooling boundary with bootstrap-local `executionInspect(source, inspectorSource)`. The selected source executes directly in the fresh initial Process activation, ordinary Actor-domain work is dispatched to cooperative idle, and inspection proceeds only when no live tasks remain. The inspector source must evaluate to one Closure, which receives the still-live source result inside the same Process/activation/domain; only the inspector terminal observation and private streams cross the existing detached boundary.
- Keep the mechanism test-neutral: no TestPlan, manifest, Future-state, stored/fresh, Error expectation, assertion, reporting, retry or timeout policy is added. Existing `execution` root-task behavior is unchanged. Generic focal evidence uses a non-transferable Closure capturing a Future: source work reaches terminal before inspection and the final scalar result alone is detached. F3C1 remains IN_PROGRESS; F3C1B becomes READY to add only Protos-owned positive `stored:Error` policy. No normative specification or Core language change.

## 0.2.240-SNAPSHOT

- Close `DIST001-E4B2 — exact candidate POM version transition`. Add `dist/transition_release_candidate_version.py` plus isolated Git-worktree fixtures. The helper accepts only an E4B1-registered clean detached worktree at selected baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`, verifies project version `0.2.236-SNAPSHOT`, and changes only the root Protos Maven project token to `0.2.236`. The resulting POM must equal baseline bytes except for that token; only `pom.xml` may be dirty, nothing may be staged, and HEAD/detached state must remain unchanged. E4B3 becomes READY for candidate commit creation. This slice creates no real candidate worktree/commit, branch, tag, GitHub Release, or release asset and does not modify implementation/runtime/specification/license/workflow state.
- Close `TOOL001-F2D3C1 — read-only Package Tool preflight -> detached plan` and formally subdivide F2D3C into C1/C2/C3. Add `ProtosWorkspacePackagePreflight`: one fresh semantic bundled-Package-Tool Process receives empty args/environment, no standard streams, no default Filesystem and exactly one confined read-only `projectTreeFilesystem`; it executes the published `self:ExecutionPlan.build` path, detaches the resulting ordinary plan through F2D3A, and terminates the tool Process before returning only immutable host DTO data.
- Prove the authority/lifecycle cut explicitly: the successful workspace fixture leaves `protos.toml` and `protos.lock` byte-identical, the observed tool Process is TERMINATED with no default Filesystem grant, and a stale workspace fails closed while still terminating the tool Process. No application Process, CLI command, lock mutation, version selection, normative specification or native boundary is added.
- `TOOL001-F2D3C2` becomes READY for separately-authorized application execution; final public workspace-run wiring remains C3 and dependency-gated.

## 0.2.239-SNAPSHOT

- Close `DIST001-E4B1 — selected-baseline detached-worktree guard`. Add `dist/prepare_release_candidate_worktree.py` and isolated Git fixtures. The helper consumes the frozen E4A selection `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` / `0.2.236-SNAPSHOT` -> `0.2.236`, requires that baseline to remain in `origin/main` lineage, rejects existing/in-checkout destinations, creates only a detached local worktree, and verifies exact HEAD/version, cleanliness, detached state and unchanged branch refs. Post-create failures remove only the invocation-owned worktree. Further decompose E4B into B1-B4; E4B2 becomes READY. Also reconcile the E4 ledger parent row with the exact persisted E4A selection values. No real candidate worktree/version mutation/commit, production implementation, normative specification, implementation version, runtime contract, workflow, license term, Git branch/tag, GitHub Release, or release-asset publication change.
- Close `TOOL001-F2D3B2C — std: delegation + resolver closure`, parent B2 and parent F2D3B. Exact `std:` specifiers and `std:` ModuleKeys delegate to one explicitly selected Standard Library resolver; workspace keys remain PackageId + internal logical-module identities and bare/foreign spellings fail closed.
- Keep the two-argument resolver constructor package-only with a rejecting standard delegate so B2C does not silently select distribution authority before F2D3C. Add real-Protos std-import focal coverage plus B2A/B2B/B1B3 regressions. No normative specification, native-boundary or CLI/application-authority change. `TOOL001-F2D3C` becomes READY.

## 0.2.238-SNAPSHOT

- Close `DIST001-E4A — exact first-prerelease selection freeze`. After the published I023/B007 closure, persist the explicitly authorized exact release basis `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` / `0.2.236-SNAPSHOT` -> `0.2.236`, future tag `v0.2.236`, and specification revision `0.1.382` in a machine-readable E4 selection record. Further decompose E4 into E4A selection, E4B detached candidate materialization, E4C archive/envelope/audit preparation, and E4D immutable full validation. E4B becomes READY. Candidate source revision remains unmaterialized and release publication remains explicitly unauthorized; no implementation, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release asset changes.
- Close `TOOL001-F2D3B2B — dep: edge/export routing`. Extend the exact workspace package resolver so `dep:<alias>/<public-export-name>` is resolved only relative to the importing workspace PackageId, through that declaring package's exact detached dependency alias edge, then through the target package's exact validated public export map. The target export's internal logical module is located only by the closed B1B3 source mechanism and encoded as the closed B1A `target PackageId + internal logical module` identity.
- Preserve dependency aliases and public export names as lookup relations rather than identity: different aliases/exports converging on the same target PackageId and internal logical module produce one canonical ModuleKey. Reject absent/foreign/out-of-plan importers, malformed dependency routes, unknown or wrong-case aliases, missing/wrong-case exports, direct spelling of an unexported physical target module, duplicate declaring-package aliases and edges targeting packages outside the installed plan. A dependency module's later `self:` imports remain relative to the target PackageId.
- Add Protos-source focal fixtures executed through the real Core import/module runtime: a root package imports an exported dependency facade whose implementation performs a target-local `self:` import; separate negative fixtures prove an existing physical target module cannot bypass exports and a package without the declaring edge cannot borrow another package's alias. Keep `std:` and final resolver closure for B2C. `TOOL001-F2D3B2C` becomes READY; no CLI/application-authority, normative specification or native-boundary change occurs.

## 0.2.237-SNAPSHOT

- Close `TOOL001-F2D3B2A — self: routing`. Add the first package-backed `ProtosModuleResolver` surface over the already-closed workspace PackageExecutionPlan, package-directory/source lookup, and canonical PackageId + logical-module ModuleKey codec. Root application bootstrap obtains its entry identity explicitly with `entryModule(logicalName)`; ordinary `self:` resolution is importer-relative and never invents an ambient root package when no importing ModuleKey exists.
- Resolve `self:<logical-module>` only from a canonical workspace ModuleKey whose exact PackageId belongs to the installed detached plan, validate/locate the target through B1B3, and preserve that PackageId in the returned canonical ModuleKey. Package exports are intentionally irrelevant to `self:`. Source loading accepts only canonical workspace keys backed by the current plan and reads the exact confined B1B3 source as UTF-8.
- Add Protos-source focal fixtures executed through the real Core import/module runtime for root-package and member-package `self:` imports, including a non-exported member module. Add host-boundary negatives for absent/foreign/out-of-plan importers and keep `dep:`, `std:` and bare specifiers closed. `TOOL001-F2D3B2B` becomes READY; no dependency-edge/export routing, standard delegation, CLI/application-authority wiring, normative specification or native-boundary change occurs.

## 0.2.236-SNAPSHOT

- Close `I023-D`, parent `I023`, and blocker `B007` after final cross-slice standard-`while` validation. Reconcile all 26 retained I023 Protos conformance fixtures, focused replay-retention and Core native-boundary architecture guards, and the full Maven suite. Correct stale current-count prose in `CORE_NATIVE_BOUNDARY.md`: the executable/provider inventory is 111 production `nativeClosure` construction sites across 30 Core providers, unchanged since I023-A; I023-B/C/D add no production native site. Freshly re-audit the documentation dependency and move `DOC001-E` from `BLOCKED_BY_DEPENDENCIES` to `READY` while leaving the control-flow chapter itself unpublished. No production runtime, normative specification, implementation-version, native-boundary, or license-term change.
- Close `TOOL001-F2D3B1B3 — logical module -> exact regular .protos source`. Add one host-only package source lookup over the closed immutable package-directory index. It validates the already-frozen portable internal logical module name, traverses each segment by exact stored spelling, appends `.protos` only to the final validated segment, and returns the canonical real regular source file.
- Reject ASCII case-fold ambiguity at every physical module component, wrong-case-only matches, missing/non-regular targets, and any directory or source symlink whose real target escapes the selected package root. Confinement is package-local rather than merely workspace-local, so one member cannot reach another member's source through an in-workspace symlink. In-root symlinks remain permitted and `core` remains an ordinary package logical module name.
- Add focused Java host-path tests covering root/member/nested source mapping, exact spelling, case-fold ambiguity, invalid runtime names, non-regular targets, in-package symlinks, workspace escapes and cross-package escapes. No `self:`/`dep:`/`std:` routing, import dispatch, CLI/application authority, normative specification or native boundary changes occur. F2D3B1B/F2D3B1 close and F2D3B2/F2D3B2A become READY.

## 0.2.235-SNAPSHOT

- Close `DIST001-E3C3 — candidate gate composition and E3 closure`, parent `DIST001-E3C`, and parent `DIST001-E3`. Add `dist/validate_release_candidate.py` plus composition/negative fixtures. The gate binds candidate archive SOURCE to clean checkout HEAD, independently verifies the E3B envelope, requires an exact candidate audit recording explicit user selection plus PASS capability/limitation/blocker reviews and `release_publication_authorized=false`, binds that audit to the current specification revision, rejects an existing `vV` tag locally or on origin, then executes the E3C1 public-prerelease B5 gate against the same archive. `DIST001-E4` becomes READY for a later explicit exact baseline/public-version selection decision. No concrete baseline/candidate/public version, `src/`, `protos/lib/`, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release-asset publication change.
- Close `I023-C4` and parent `I023-C` with final repeated cross-phase suspension/replay closure. Add Protos Future conformance in which every one of four logical condition activations and three reached body activations creates and awaits a distinct child Future; exact result `443343016` proves all pre/post-suspension effects, child executions and observed values occur exactly once across alternating callback phases. Extend the Java-only replay-retention mechanism test to suspend in every condition and body, comparing the same active condition suspension after 8 versus 1024 completed suspending iterations and requiring identical bounded retained evaluator-event and invocation-activation counts. `I023-D` becomes READY. No production runtime, specification, implementation-version, native-boundary, or license-term change.
- Close `TOOL001-F2D3B1B2C — immutable package -> physical-directory binding` and parent `TOOL001-F2D3B1B2 — exact member-location directory binding`. Compose the closed B1B1 project/package index with the closed B1B2B confined canonical traversal into one immutable host index whose records pair each detached workspace PackageNode with its exact physical real directory.
- Bind root `location = ""` exactly to the anchored real project root and every non-root location only through B1B2B; index those immutable bindings by exact opaque PackageId and exact canonical location without basename fallback, recursive search, case folding, normalization, or source-file lookup. Missing/unbindable members fail closed before a resolver can use the plan.
- Add focused Java host-integration tests for root/member binding, exact PackageId/location lookup, root-only workspaces and missing physical members. No logical module parsing, `.protos` lookup, ModuleKey construction, `self:`/`dep:`/`std:` routing, CLI behavior, Protos-observable import semantics or native boundary changes occur. `TOOL001-F2D3B1B3` becomes READY.

## 0.2.234-SNAPSHOT

- Close `I023-C3 — cooperative cancellation / ensure composition` without a production change. Add Protos Future conformance that self-requests cancellation inside the first reached while body, completes that body, reaches the second condition and second body, and only then hits an ordinary `Future.value()` cancellation-observation boundary. Exact observer result `22110` proves `while` adds no per-iteration cancellation poll, the second callback pair is reached after the request, code after the actual observation boundary does not execute, and outer `ensure` cleanup runs exactly once during unchanged cancellation unwind. `I023-C4` becomes READY. No runtime, specification, implementation-version, native-boundary, or license-term change.
- Close `TOOL001-F2D3B1B2B — confined canonical member-location traversal`. Add one host-only traversal that consumes a canonical non-root workspace location, splits only on literal `/`, delegates every stored-name lookup to the closed B1B2A exact-child primitive, resolves every selected directory to its real path, and continues only while that real path remains under the selected real project root.
- Freeze the B1B2 symlink policy mechanically: directory symlinks are permitted only when their resolved target stays inside the real project root; escaping links fail closed. Every subsequent component is traversed from the resolved in-root directory, and the final result is the canonical real member directory. No package-index application, PackageId binding, `.protos` lookup, ModuleKey construction, resolver routing or CLI behavior is added.
- Add focused Java host-path tests for exact multi-component traversal, in-root symlink continuation, escaping-symlink rejection, malformed/non-canonical locations and invalid roots. Keep Protos-visible import conformance deferred to resolver routing. `TOOL001-F2D3B1B2C` becomes READY.

## 0.2.233-SNAPSHOT

- Close `I023-C2 — body suspension/replay exact-once closure` without a production change. Add a Protos Future conformance case with three logical condition activations and two reached bodies; each body creates a distinct task-backed child Future and immediately observes it with `Future.value()`, forcing two real cooperative suspensions. Exact encoded result `322223` proves three conditions, two pre-suspend body effects, two post-resume effects, two body activations, two child executions, and observed child results `1 + 2`. This composes D044 body-phase replay with B1 completed-callback compaction and detects duplicated/skipped body effects or accidental prior-iteration reuse. `I023-C3` becomes READY. No runtime, specification, implementation-version, native-boundary, or license-term change.
- Close `DIST001-E3C2 — independent release-envelope verifier`. Add `dist/verify_release_metadata.py` plus corruption-focused fixtures. The verifier independently binds one exact public-prerelease ZIP to the E3B envelope's exact file set, v1 manifest key set, SOURCE/RUNTIME and baseline identity, public version/tag, specification revision, archive/checksum/notes digests, basename-only external checksum content, and release-note identity with explicitly populated capability/limitation sections. Archive, checksum, notes, manifest, and extra-file tampering all fail closed. E3C3 becomes READY for final generic candidate-gate composition. No concrete baseline/candidate/public version, `src/`, `protos/lib/`, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release-asset publication change.
- Close `I023-C1B` and parent `I023-C1` with repeated real condition suspension across completed loop iterations, without changing production code. The new Protos Future conformance case creates a fresh child Future inside every logical `while` condition activation and immediately observes it with `value()`, forcing three separate cooperative suspensions. Exact encoded result `33236` proves three pre-suspend condition effects, three post-resume effects, two body activations, three distinct child executions and observed child results summing to six. This composes C1A replay stability with B1 completed-callback compaction and detects duplicated, skipped or accidentally reused callback work. `I023-C2` becomes READY. No runtime, specification, implementation-version, native-boundary or license-term change.
- Close `TOOL001-F2D3B1B2A — exact direct-child directory lookup`, the first sub-slice of B1B2. Add one host-only primitive that enumerates an already-selected parent directory and accepts only a child whose stored filename String exactly equals the already-separated workspace location component and whose target denotes a directory.
- Preserve the normative component boundary mechanically: B1B2A never feeds the component to host path parsing, performs no case folding/Unicode normalization, and rejects empty/`.`/`..` plus `/` because `/` belongs to the workspace-location splitter. It deliberately does not call `toRealPath()` or decide symlink confinement; B1B2B owns canonical multi-segment traversal and in-root real-path enforcement.
- Add focused Java host-path tests for exact spelling, missing/non-directory children, invalid single-component inputs and non-directory parents. No PackageExecutionPlan-wide binding, `.protos` lookup, ModuleKey construction, resolver routing, CLI behavior, Protos observable import semantics or native boundary changes occur. `TOOL001-F2D3B1B2B` becomes READY.

## 0.2.232-SNAPSHOT

- Close `DIST001-E3C1 — release-aware B2/B5 identity and conformance plumbing`. Keep development `V-SNAPSHOT` verification as the default while extending `dist/verify_portable.py` and `dist/validate_portable.sh` with an explicit public-prerelease mode. Release mode requires public `V`, clean exact candidate `HEAD`, exact `V-SNAPSHOT` baseline/version/ancestry and coherent E3A SOURCE release metadata before the existing B3 caller-CWD/Package Tool, B4A Test Tool and B4B exact optimizing-runtime checks run against the same immutable ZIP. Add isolated public-mode identity/provenance regression tests and further decompose E3C into C1-C3; E3C2 becomes READY. No concrete baseline/candidate/public version, `src/`, `protos/lib/`, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release-asset publication change.
- Close `I023-C1A — single condition suspension/replay exact-once evidence` without a production change. Add a Protos-source Future conformance case whose first reached `while` condition increments state, suspends at ordinary `Future.value()`, resumes the same logical activation, performs its post-resume effect exactly once, runs one body, and then completes a second false condition. The encoded result must be exactly `221`, so replay that duplicates the pre-suspension condition effect cannot pass. Allocate C as C1 condition replay, C2 body replay, C3 cancellation/ensure, and C4 repeated-suspension/bounded-state closure; C1B becomes READY. Re-run the temporary Java owner of `future-*`, the D4 non-Future ownership guard, the while replay-compaction focal, and the full suite. No runtime/specification/implementation-version/native-boundary/license-term change.
- Close `DIST001-E3B — release notes and asset/checksum manifest envelope`. Add deterministic `dist/prepare_release_metadata.py` plus isolated fixtures. The generator accepts an already-built clean public-prerelease ZIP, derives release/source/baseline/runtime identity from its own SOURCE/RUNTIME metadata, requires an explicit numeric specification revision and explicit capability/limitation claims, and emits `RELEASE_NOTES.md`, `RELEASE_MANIFEST.txt`, and a basename-only portable archive `.sha256`. The manifest records archive, checksum, and notes digests for E3C verification. E3C becomes READY. No concrete baseline/candidate/public version, `src/`, `protos/lib/`, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release-asset publication change.
- Close `DIST001-E3A — release-mode builder and provenance/version guards`. Preserve the existing development `V-SNAPSHOT` distribution path and add an explicit fail-closed `--public-prerelease --release-baseline <exact-sha>` mode. Generic release identity logic requires a clean public `V` candidate, an exact baseline whose `pom.xml` is `V-SNAPSHOT`, and baseline ancestry; release-mode `SOURCE.txt` records baseline/candidate revisions separately with `artifact_kind=public-prerelease`, `public_release=true`, `release_version=V`, and `release_tag=vV`. Add isolated Python fixtures for strict version mapping, development-mode regression, public metadata, dirty-candidate rejection and baseline-version mismatch. Subdivide E3 into E3A-E3C; E3B becomes READY. No concrete baseline/candidate/public version is selected; no source under `src/` or `protos/lib/`, normative specification, implementation version, runtime contract, license term, workflow, Git tag, GitHub Release, or release asset publication changes.
- Close `TOOL001-F2D3B1B1 — selected project-root anchor + detached package index` and further decompose the physical-source parent into B1B1 root/index, B1B2 exact member-directory binding, and B1B3 logical-module source-file binding. This keeps representation, workspace path traversal, and source lookup independently publishable.
- Add host-only `ProtosWorkspacePackageProjectIndex`: anchor the caller-selected project root as normalized + real directory state and index the already-detached immutable PackageExecutionPlan by exact opaque PackageId and exact location String. Reject empty/duplicate PackageIds, duplicate locations, empty package sets, unknown lookups and disagreement between the plan root ref and the unique empty root location. Do not traverse non-empty member locations in this slice.
- Add focused Java tests for the host Path/DTO index boundary, including deterministic root/package/location lookup and fail-closed missing/non-directory project root, duplicate/mismatched plan index shape, while explicitly proving member-location traversal is deferred. No module source lookup, resolver, `self:`/`dep:`/`std:` routing, CLI change, Protos semantic test or native Closure boundary is added. `TOOL001-F2D3B1B2` becomes READY.

## 0.2.231-SNAPSHOT

- Close `DIST001-E2 — coherent public pre-release version contract`. Select the generic mapping from an explicitly selected `V-SNAPSHOT` development baseline to public version `V`, Git tag `vV`, GitHub Release title `Protos V`, and `prerelease=true`, with POM/JAR/`protos --version`/distribution/archive/SOURCE identity all required to agree on `V`. Define an isolated candidate-commit model derived from the selected baseline so active `main` remains on its ordinary SNAPSHOT development sequence; record baseline and candidate revisions separately and forbid unrelated implementation changes in the release transition. `DIST001-E3` becomes READY to implement generic release metadata/assets/validation machinery. No concrete baseline, candidate, public version, executable, workflow, normative specification, implementation version, license term, Git tag, GitHub Release, or release asset is changed or selected.
- Close `I023-B2D2`, parent `I023-B2D`, `I023-B2`, `I023-B`, and `B008` after D045/spec `0.1.382` resolves the task-scoped structured-ownership boundary. Add Protos-source conformance in which a `while` body returns a newly-created task-backed Future from inside an enclosing asynchronous task: the post-loop parent effect occurs before the child effect (`12`, not activation-drain `21`), while observation of the enclosing Future cannot complete until that non-detached child has terminalized. Re-run the complete non-Future main-manifest Test Tool corpus, including all B2 evidence and the JSON text-adapter overlap canary that falsified per-synchronous-activation draining. No production runtime, normative specification, implementation-version, native-boundary, or license-term change. `I023-C` becomes READY.
- Close `TOOL001-F2D3B1A — canonical workspace ModuleKey codec` and further decompose F2D3B by independently valid identity, physical-source, and routing boundaries. Add host-only `ProtosWorkspacePackageModuleKey`: canonical workspace module identity depends exactly on opaque non-empty PackageId plus the F2D1 portable internal logical module name and serializes under the workspace-specific `pkg-workspace:v1:` domain with canonical URL-safe unpadded UTF-8 base64 components.
- Keep non-identity data out of the key: dependency/export aliases, workspace member location, selected project root, checkout/cache paths and Filesystem state do not participate. Decode accepts only canonical spelling and revalidates the portable logical name. The workspace-specific domain deliberately leaves future registry/Git immutable-node identity free to use a non-colliding key contract.
- Add a focused Java host-boundary test for deterministic identity, PackageId exact/opaque round-trip, module/package distinction, domain ownership and malformed/non-canonical key rejection. This focal is Java intentionally: normative Core defines ModuleKey as an internal host-produced identity that need not be exposed as a Protos object; observable `import("self:...")` / `import("dep:...")` behavior is deferred to B2A/B2B and will prefer Protos-source conformance. No Filesystem/source loading, resolver implementation, CLI change or native Closure boundary is added. `TOOL001-F2D3B1B` becomes READY.

## 0.2.230-SNAPSHOT

- Close `DIST001-E1 — first pre-release readiness and candidate envelope`. Record that DIST001-A/B/C/D provide sufficient release-engineering infrastructure to begin bounded pre-release preparation, while explicitly leaving both the candidate source revision and public pre-release version UNSELECTED. Persist the E1-E6 execution plan, exact candidate eligibility gate, current limitation/claim boundary (including open I023/B008), selected GraalVM JDK22 + Truffle 24.0.0 runtime scope, and the rule that E1-E3 cannot create or authorize a public tag/GitHub Release. `DIST001-E2` becomes READY for the coherent public-version contract. No normative specification, implementation version, executable, workflow, license term, Git tag, GitHub Release, or release asset changes.
- Resolve B008 normatively with D045 / specification revision `0.1.382`: structured ownership is task-scoped at asynchronous execution boundaries, not per ordinary synchronous Closure/method activation. Returning/storing/wrapping a pending task-backed Future does not wait, detach, transfer, re-parent or otherwise change its ownership edge; distinct async child tasks own their own descendants and the enclosing async owner drains non-detached children only at its own terminal completion.
- Reconcile `Future.then`, P, `Future.detach()`, D043 `ensure`, D044 `while` and the README summary with that single rule. `while` still ignores body Future results without implicit await/adopt/flatten/cancel/detach/re-parent/scheduling; B008 moves BLOCKED -> READY and I023-B2D2 becomes READY for implementation/conformance closure. No runtime, implementation-version, license-term, syntax or Future state/identity change.

- Close `DIST001-D2B — observed repaired CI snapshot artifact closure`, parent `DIST001-D2`, and parent `DIST001-D`. `Distribution snapshot` run `34101588533` for exact source `994429173b6ec0fc086f307f4a49815f219c6523` completed successfully through the full Maven suite, portable build, complete B5 gate, portable checksum preflight, and artifact upload. Artifact `protos-snapshot-994429173b6ec0fc086f307f4a49815f219c6523` (id `10010752033`, Actions digest `sha256:ada6593e40efee2e78981d0d0881817b5061250db6dbbd8946ad93b786e049f4`) was downloaded and independently inspected: it contains `protos-0.2.230-SNAPSHOT-posix-jvm.zip` plus its `.sha256`; the checksum records only the ZIP basename, matches `f66f011ba9a7c579f81b5aad7098bd4ec421ebc8117b3c4c8954374441e94337`, and passes `sha256sum -c` outside the runner workspace. `DIST001-E` becomes READY for bounded prerelease-readiness work, but no tag or GitHub Release is authorized without a later explicit exact-candidate user decision. No normative specification, implementation-version, executable, workflow, or license-term change.
- Record `B008 — Structured ownership when a task-backed Future escapes an activation` after I023-B2D2 adversarial work exposed a genuine normative ambiguity rather than a loop-runtime defect. Current Core text simultaneously permits ordinary functions/P to return Future-shaped results owned under creating-activation structured rules and requires a normally completing owner to wait for every non-detached child, but does not define the ownership/lifetime transition when the created task-backed Future is itself the activation's exact returned value.
- Mark `I023-B2D2` BLOCKED on B008 and keep I023-B2D/B2/B in progress. D044/B007 remain unchanged: `while` still ignores a normal body Future result without implicit await/adoption/flattening/cancellation; the unresolved question is the independent structured-lifetime rule supplied by Future/Task semantics. Diagnostic activation-drain experiments were never published and are not treated as normative evidence. No runtime, specification, implementation-version, native-boundary, license-term, or conformance-manifest change.

- Close `DIST001-D2A — portable external snapshot checksum repair`. Observed D1 workflow run `34100296135` for source `dc64fb8c4103b44020cd1b718d6850028f08b3a5` completed green and uploaded artifact `10010254675`, but downloaded-artifact inspection found that its external `.sha256` embedded the GitHub runner's absolute archive pathname. The digest value itself matched the portable ZIP. Generate the checksum from the archive directory so it records only the ZIP basename, and require `sha256sum -c` to pass before upload. `DIST001-D2B` becomes READY to observe a repaired real CI artifact; parent D remains open. No Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL001-F2D3A — immutable host DTO + defensive plan detach` and formally subdivide the previously monolithic F2D3 handoff into F2D3A detach, F2D3B exact workspace resolver, and F2D3C command-scoped preflight/application-authority separation. This is implementation decomposition only; F2D remains workspace-only and external registry/Git nodes remain fail-closed.
- Add immutable host-internal `ProtosPackageExecutionPlan` plus `ProtosPackageExecutionPlanAdapter.detach(...)`. The adapter defensively validates the already-published generation-1 ordinary Protos ABI and recursively copies it into immutable records/Lists/Maps. PackageId remains opaque; Java does not parse manifests/locks or invent package semantics.
- Add focused F2D3A host-boundary tests over the existing F2D2 physical workspace fixture, including mutation-detachment proof and fail-closed malformed generation, unexpected shape, escaping member location and invalid runtime alias. No resolver, CLI dispatch, package selection, external materialization, lock mutation or native Closure boundary is added. `TOOL001-F2D3B` becomes READY.

## 0.2.229-SNAPSHOT

- Publish `TOOL002-F2 — child-local failed/cancelled Future terminal mechanism`. Bundled `Runner.protos` now recognizes, but still does not publicly select, `future-error`, `future-error-parent`, and `future-cancelled`. The expectation-private child envelope calls ordinary `Future.value()` twice: a FAILED Future must re-signal the same stored Error identity, while a CANCELLED Future must signal two fresh standard `Cancelled` occurrences. `future-error-parent` additionally requires the exact immediate standard Error parent. No Test-only Future state reflection or host dispatch loop is added.
- Add Protos-owned mechanism evidence over retained closed-TextReader failure, retained InvalidIOArgument Future failure, and retained pre-start cancellation, plus cross-family negative cases proving FAILED/CANCELLED are not collapsed and Error-parent matching is immediate/exact. Java only provisions existing host mechanisms. Public `runSimple` remains unchanged until F4; F3 becomes READY for `future-observation-error-identity`. No Core/runtime/specification change.

## 0.2.228-SNAPSHOT

- Close `DIST001-D1 — CI snapshot workflow definition`. Add `.github/workflows/distribution.yml` for `main` pushes and explicit manual dispatch. The job selects exact GraalVM Community JDK 22.0.0, runs the full Maven suite, builds a clean portable ZIP, executes the complete B5 extracted-distribution gate, writes an outer SHA-256, and uploads the ZIP/checksum as a 14-day `protos-snapshot-<full-source-sha>` GitHub Actions artifact using `actions/upload-artifact@v7`. D remains IN_PROGRESS until D2 observes a real green run and matching artifact. No binary is published as a GitHub Release, and no Git tag, normative specification, implementation-version, or license-term change is made.
- Publish `TOOL002-F1 — child-local resolved Future expectation mechanism`. Bundled `Runner.protos` now recognizes, but does not yet publicly select, `future-integer`, `future-null`, and `future-boolean` for an expectation-private child-source envelope. The retained case source creates its Future inside the same fresh semantic Process; ordinary `Future.value()` performs all pending-work suspension/resume there, and the D1 detached boundary receives only canonical Boolean evidence. Live Future transfer remains forbidden.
- Add Protos-owned mechanism evidence over retained `future/then-transforms.protos`, retained `future/closure-future-null.protos`, an inline Boolean Future, mismatch, non-Future and failed-Future cases. Java only provisions the existing Test Tool Prelude, confined corpus Filesystem and exact execution capability. `runSimple`/D4 selection is deliberately unchanged until F4. Subdivide remaining TOOL002-F into F2 terminal failed/cancelled identity, F3 observation-error identity, and F4 activation/Java ownership cutover. No Core/runtime/specification change.

## 0.2.227-SNAPSHOT

- Close `DIST001-B5 — cross-slice distribution closure` and parent `DIST001-B`. Add `dist/validate_portable.sh` as the single reusable complete distribution gate: one exact archive first passes B2 clean-source identity/checksums, then B3 outside-checkout caller-CWD + Package Tool, B4A bundled Test Tool, and B4B exact GraalVM Community JDK22 + Truffle 24.0.0 `HotSpotTruffleRuntime`. The gate hashes the outer ZIP before/after all disposable extractions to prove validation does not mutate the artifact. `DIST001-D` becomes READY to reuse the gate for CI snapshot artifacts. No binary publication, Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL001-F2D2 — pure workspace execution-state + plan construction`. Refactor `self:ResolutionRoot` into a single physical parse path: new `assembleState(projectTreeFilesystem)` returns the unchanged F2B `ResolutionRootV1` together with full root/member `ManifestV1` package records and their projected semantic manifests; the existing `assemble(...)` surface remains a compatibility projection and preserves F2C behavior.
- Add bundled-Protos `self:RuntimeNames` and `self:ExecutionPlan`. RuntimeNames implements the F2D1 portable ASCII segment/logical-module validator including case-insensitive Windows reserved-name rejection. `ExecutionPlan.build(projectTreeFilesystem)` loads one canonical lock, verifies the F2B3 resolution-input header, rejects all registry/Git nodes, reconciles exact root/workspace-member PackageIds/declarations and exact per-declaring-package path dependency alias->workspace target edges, validates/copies ManifestV1 exports, and returns a fresh inert generation-1 workspace `PackageExecutionPlanV1`.
- Add Protos-owned physical conformance for root-only/export aliases, workspace bidirectional path edges and target exports, plus fail-closed stale header, invalid runtime export name, workspace-member body mismatch, external lock node, missing locked path edge and registry-dependency graphs. Java only provisions the existing confined read-only project-tree Filesystem and executes the fixtures. No CLI, production Java/native boundary, resolver installation, lock mutation, version selection, cache scan, fetch or external materialization is introduced. `TOOL001-F2D3` becomes READY.

## 0.2.226-SNAPSHOT

- Close `DIST001-B4B — exact optimizing-runtime probe` and parent `DIST001-B4`. Add a bounded exact-runtime smoke plus a tiny Java probe. The publication launcher requires exact GraalVM Community JDK 22.0.0 before touching repository state, provisioning the official Linux x64/aarch64 archive into an external user cache when necessary and verifying the release-provided SHA-256. The smoke extracts the B2-verified bundle outside the checkout, preserves its optimizer JARs intact, passes the launcher supported-runtime gate without the override, compiles against the extracted Protos/Truffle classpath, and requires exact `com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime` with Truffle 24.0.0. `DIST001-B5` becomes READY. No bundled JDK, Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL002-E3 — Java Package/TOML ownership retirement`. Remove `ProtosPackageToolTomlSyntaxConformanceTest`, the legacy JUnit path that independently parsed `protos/tests/package-tool/toml-syntax/manifest.tsv`, interpreted retained `true`/`error` expectations and directly compiled/executed every TOML fixture. The E1A/E1B/E2A/E2B bundled-Protos path is now the sole owner of retained corpus planning, source loading, execution, expectation interpretation and aggregation.
- Retain Java tests only for host-side Test Tool mechanics such as confined Filesystem provisioning, selected bundled Package resolver execution and detached cross-Prelude observation. This is test-impact/project-state-only retirement: no production source, distributable Protos source, specification, authority boundary, language semantics or implementation-version change. `TOOL002-E4` becomes READY.
- Close documentation/design slice `TOOL001-F2D1 — PackageExecutionPlan ABI + runtime-name/preflight contract` without changing the implementation version. Define a bounded workspace-only normal-execution subset: a canonical non-stale lock must match the physical root/member PackageIds/declarations and exact path-dependency alias->target edges; registry/Git lock nodes or declarations fail closed until exact materialization plus mandatory ContentIdentity verification exists.
- Freeze the initial package-backed runtime naming policy above structural manifest schema v1. Dependency aliases are one portable ASCII segment; export keys/values and internal module names are portable `/`-separated logical names using the existing standard-resolver segment discipline (without the `std:`-specific first-segment `core` reservation). `self:<logical-module>` addresses the current package internally; `dep:<alias>/<public-export>` follows the exact locked edge then the target manifest export map, so dependency consumers cannot bypass exports.
- Define inert ordinary-Protos `PackageExecutionPlanV1` ownership and host handoff: preserve full ManifestV1 execution metadata beside F2B `ResolutionRootV1`, project workspace refs/relative locations/exports/dependency edges, then defensively validate and detach the plan into immutable host resolver state. Package Tool Filesystem/store/network authority never transfers to the application through the plan. `TOOL001-F2D2` becomes READY for pure plan construction and F2D3 remains dependency-gated.

- Close `TOOL002-E2B — Package/TOML Protos-owned expectation execution` through E2B2. `Main.protos` now executes the retained Package/TOML TestPlan with the existing bundled `Runner.runSimple`, the separate E1B `packageTomlFilesystem`, and the E2A `packageExecution` facility. No Package-specific expectation branch or resolver is added to Runner.
- Add one ordinary-Protos full-corpus integration fixture that loads the real retained Package/TOML manifest, executes every CaseSpec through the D-owned Boolean/generic-Error policy, and requires a non-empty plan, selected count equal to plan size, zero skipped cases, passed count equal to plan size, full result cardinality and `runAllPassed`. Java only provisions the existing Package Prelude and confined corpus Filesystem, then asserts canonical true.
- Keep the legacy Java `ProtosPackageToolTomlSyntaxConformanceTest` unchanged in this slice so ownership retirement remains attributable to TOOL002-E3. E2B is CLOSED and E3 becomes READY; no normative language change or authority broadening is introduced.

## 0.2.225-SNAPSHOT

- Close `DIST001-B4A — extracted bundled Test Tool smoke`. Add a bounded `dist/smoke_test_tool.sh` that extracts the B2-validated distribution outside the checkout and runs public `protos test` from a separate temporary project, requiring the Test Tool bootstrap/argument markers after successful bundled-plan execution. On a validation JDK outside the selected JDK22 contract, isolate optimizer JARs only in the disposable copy and exercise fallback Truffle, exactly as B3 does; this does not broaden runtime support. Subdivide B4 so `DIST001-B4B` remains READY for the intact optimizer classpath and exact `HotSpotTruffleRuntime` proof. No Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL001-F2C — physical resolution-root assembly`. Add ordinary bundled-Protos `self:ResolutionRoot.assemble(projectTreeFilesystem)`, reading the physical root `protos.toml` plus exactly the root-declared workspace member manifests through an explicitly supplied confined read-only tree Filesystem, then projecting schema-v1 values into the already-frozen F2B `ResolutionRootV1`.
- Implement F2B2 path/workspace semantics at the physical boundary: canonical root-relative workspace member declarations, root-only workspace expansion, unique root/member PackageIds, D1 ReleaseVersion + D2 registry-constraint parsing, registry/Git dependency projection, and path-dependency `.`/`..` normalization relative to the declaring package with fail-closed root escape and target restriction to the root or an explicit member. Member-local workspace declarations are not recursively expanded.
- Reuse the already-general `ProtosNioReadOnlyTreeFilesystemBackend` only as the focal host capability; no Core/Filesystem/native API changes and no CLI-wide project-tree grant are introduced. Protos fixtures own root-only/workspace/dependency behavior plus invalid member path, duplicate PackageId, missing member, undeclared path target and root-escape failures. F2 remains open for command-scoped run-preflight, exact PackageExecutionPlan construction/handoff and later resolve/update work.

## 0.2.224-SNAPSHOT

- Publish `TOOL002-E2B1 — Package/TOML generic-error CaseSpec normalization`. Retained Package/TOML `error` rows now map to the D3A2 canonical generic-error CaseSpec representation with `expectation == "error"` and `expected == "-"`, matching `Runner.evaluateSimple` and the selected Test Tool architecture. This corrects the E1A planning-only placeholder discovered when E2B began composing that plan with the D-owned runner; no Package/TOML fixture execution is added here.
- Update the existing Protos planning fixture to assert the canonical `-` sentinel. No language semantics, resolver behavior, Filesystem authority, Runner policy, Java corpus ownership or specification text changes. E2B2 becomes READY for full retained Package/TOML plan execution and full-corpus Protos-owned evidence.

## 0.2.223-SNAPSHOT

- Close `DIST001-B3 — outside-checkout CWD and Package Tool smoke`. Add a bounded POSIX smoke that extracts the B2-validated ZIP outside the repository, creates a separate caller project, executes a relative `.protos` source, and validates caller-local `protos.toml` through public `protos package manifest`. When the validation host is outside the selected GraalVM Community JDK 22 contract, disable optimizer JARs only in the disposable extracted copy and use the documented unsupported-runtime override so B3 tests relocation/CWD through fallback Truffle rather than an unsupported optimizer/JDK pairing. Captured launcher stdout/stderr is now emitted on smoke failure. `DIST001-B4` becomes READY. No Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL001-F2B3 — resolution-input digest + stale comparison` and parent `TOOL001-F2B`. Publish ordinary bundled-Protos `self:ResolutionInput` over the F2B1/F2B2 semantic `ResolutionRootV1`: exact UTF-8/LF domain-separated canonical bytes, F1B1 qstring scalar encoding, root-first/member-location ordering, alias-ordered dependency projections, D2 semantic constraint normalization and validated root/member/path/compatibility invariants. Raw TOML, source ordering, host paths, exports, package locator, caches and lock output remain excluded.
- Hash canonical semantic bytes only through closed reusable `std:crypto/SHA256`, render exactly 64 lowercase hexadecimal digits and expose the lock-header identity `protos-resolution-input-v1 sha256:<digest>`. `matchesHeader` also requires lock-format 1 and resolver-version 1. Extend F2A `self:LockFile` with read-only `isStale(filesystem, semanticRoot)`: canonical load failures remain ordinary failures; a successfully loaded but mismatching resolver/header input returns stale. No resolve/discovery/fetch/update/publication side effect occurs.
- Add Protos-owned conformance for exact canonical bytes/digest, member/dependency ordering, D2 interval spelling equivalence, compatibility participation, normalized path-target identity, header match/mismatch and fail-closed duplicate/member/path/compatibility invariants, plus a confined-Filesystem stale/fresh integration harness. `TOOL001-F2` and parent F remain open for later normal-execution lock consumption and explicit resolve/update policy after fresh audit.

## 0.2.222-SNAPSHOT

- Publish `TOOL002-E2A2B — real Package/TOML failed-fixture integration`. The exact-source facility now supplies its selected execution Prelude to the E2A2A detached snapshot for completed and failed observations.
- Add one ordinary-Protos integration fixture for retained `invalid-key-error.protos`: read through E1B `packageTomlFilesystem`, execute through E2A1 `packageExecution`, and prove a failed observation with a fresh caller-domain Error whose immediate parent is the Test Tool `Error`. Java only provisions host mechanics and asserts canonical true.
- Keep parent closure separate: E2A2B closes and E2A2C becomes READY; no full TOML TestPlan traversal, expectation-policy cutover, Java-runner retirement, authority broadening, or normative specification change.

## 0.2.221-SNAPSHOT

- Close `DIST001-B2 — clean-source archive identity`. Add a non-executing `dist/verify_portable.py` guard that opens the generated ZIP directly, validates CRC and a single safe distribution root, requires `SOURCE.txt` to identify the exact clean committed `HEAD`, and requires `SHA256SUMS` to cover every distributed file except itself exactly once with matching SHA-256 values. The definitive publication candidate is rebuilt after commit before verification. `DIST001-B3` becomes READY; no extracted Protos execution, Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `LIB006-B — pure-Protos SHA-256 implementation` and bounded parent `LIB006 — deterministic hashing`. Publish ordinary Standard Library module `std:crypto/SHA256` with exactly one public module slot, `digest(bytes)`, returning a fresh 32-octet SHA-256 Bytes result. The implementation is ordinary Protos: exact non-negative Integer arithmetic, `div`/`mod`, private per-invocation fixed-32-bit helpers, standard FIPS 180-4 constants/padding/schedule/rounds and no Java `MessageDigest`, native Closure, host crypto provider, entropy, I/O or ambient capability.
- Keep Bytes handling capability-neutral and caller-safe: standard Bytes domain validation occurs without mutation, the complete input is copied before padding, the SHA-256 64-bit message-length limit fails closed, all working state is per call, and equal calls produce equal octets with fresh result identity. No public Bits/wrapping API, `digestHex`, keyed crypto, password/KDF, cipher/signature/TLS or streaming digest state is introduced.
- Add Protos-owned known-answer conformance for empty, `abc`, the standard multi-block vector, binary octets, 55/56/64-byte padding boundaries, fresh result identity, caller-input preservation and invalid non-Bytes rejection. Java contributes only the module/bootstrap harness and does not compute expected digests. With reusable `std:crypto/SHA256` now published, `TOOL001-F2B3 — resolution-input digest + stale comparison` transitions from `BLOCKED_BY_DEPENDENCIES` to READY.

## 0.2.220-SNAPSHOT

- Close documentation/governance slice `DIST001-B1 — validation hygiene and bounded smoke decomposition`. Ignore Python `__pycache__` and bytecode outputs after the DIST001-A publication launcher exposed a dirty-worktree false positive, and split the extracted-distribution gate into B2 clean-source identity, B3 outside-checkout CWD/Package Tool execution, B4 bundled Test Tool + optimizing-runtime probe, and B5 cross-slice closure. `DIST001-B2` becomes READY. No distribution behavior, binary artifact, Git tag, GitHub Release, specification, implementation-version, or license-term change.
- Publish `TOOL002-E2A2A — detached cross-Prelude standard Error taxonomy`. Add an explicit source-Prelude form of the existing test-neutral detached execution-value boundary. Source `Error` and every closed standard Error prototype are mapped by the existing Core taxonomy name to the corresponding destination-Prelude prototype before ordinary graph copying. Fresh Error occurrences retain fresh destination identity; no source Prelude prototype is shared into the destination.
- Subdivide `TOOL002-E2A2` into E2A2A mechanism, E2A2B one real Package failed-fixture integration, and E2A2C closure/reconciliation. E2A2A does not change `packageExecution`, execute a Package/TOML fixture, activate TestPlan expectation policy, or retire Java runner ownership. No normative specification change.

## 0.2.219-SNAPSHOT

- Open bounded Standard Library prerequisite `LIB006 — deterministic hashing` and close documentation/design slice `LIB006-A` without changing the implementation version. Freeze `std:crypto/SHA256.digest(Bytes) -> Bytes` as a stateless one-shot SHA-256 operation returning fresh 32-octet digest values, with no entropy, keyed crypto, password/KDF, cipher/signature/TLS surface, streaming state, Core value family or native/JVM crypto boundary.
- Select a bootstrap-safe pure-Protos implementation for `LIB006-B`: private fixed-32-bit SHA-256 helpers over exact non-negative Integer arithmetic using the already-standard `div`/`mod` primitives, with modulo-2^32 reduction and Protos-owned known-answer conformance. The private helpers do not publish a general Bits/wrapping API. `LIB006-B` becomes READY and is recorded as the concrete dependency blocking `TOOL001-F2B3`; after it publishes, F2B3 may become READY.

- Close `DIST001-A — relocatable portable distribution layout and runtime contract`. Add a constructible POSIX/JVM development ZIP under `target/distributions/` containing the shaded Protos JAR, Core/Standard Library, bundled tools, test corpus, examples, tutorials, exact source/runtime metadata and internal SHA-256 checksums. The shared `bin/protos` launcher now sets `PROTOS_HOME` without changing the caller CWD and detects checkout versus extracted-distribution layout. Distribution assembly resolves the exact PERF002-validated optimizing stack (GraalVM Community JDK 22 + external `truffle-runtime:24.0.0`) without adding that runtime to the normal project Maven build/test classpath. DIST001-B becomes READY for independent extracted execution and optimizing-runtime verification. No Git tag, GitHub Release, normative specification, implementation-version, or license-term change.

- Publish `I023-B2D1` with Protos-source conformance for the D044 body-Future boundary. A Future returned normally by a reached body is an ignored ordinary body result: a child Future that fails does not get awaited/adopted by `while` and therefore does not interrupt later loop iterations, while a separately retained successful Future remains usable after loop completion and is not cancelled or replaced by the loop. This slice deliberately leaves final structured-ownership reconciliation and cross-B2 closure to READY `I023-B2D2`; no runtime, normative specification, native-boundary, license-term, or implementation-version change.
- Publish `TOOL002-E2A1 — selected Package Tool resolver execution environment`. Generalize the existing test-neutral `ProtosExactExecutionFacility` so a host may install a named bootstrap-local exact-source executor backed by an already-selected Prelude/module resolver. The default `execution` slot remains unchanged. `protos test` now provisions `packageExecution` with the existing `ProtosBundledToolModuleResolver("package", ...)`, and every invocation still executes through TOOL002-B/C in one fresh semantic Process with private streams, empty args/environment and no child Filesystem authority.
- Prove the mechanism with ordinary Protos source: the E2A1 fixture reads the first real Package/TOML case through E1B `packageTomlFilesystem`, invokes it through `packageExecution`, and observes successful canonical `true`, demonstrating that fixture `self:TomlSyntax` resolves inside the exact bundled Package Tool environment. Do not activate the TOML plan in `Runner`, interpret the corpus as a whole, or retire the legacy Java conformance runner yet.
- Subdivide parent `TOOL002-E2A` after cross-Prelude outcome audit. E2A1 closes only the normal selected-resolver path; E2A2 remains READY to close safe failed/Error observation across the distinct Package Prelude without leaking or copying execution-environment authority. E2B remains blocked until parent E2A closes. No normative specification change.

## 0.2.218-SNAPSHOT

- Close documentation/design slice `TOOL001-F2B2 — resolution-root/workspace semantic assembly design` without changing the implementation version. Freeze one active root workspace per resolution root; canonical `/`-separated root-relative member paths over normative Path components; unique root/member PackageIds; non-recursive member workspace declarations; and path dependencies normalized relative to their declaring package but constrained to the root or one explicitly declared member.
- Define schema-v1 `compatibility.language` as an opaque exact `LanguageCompatibilityId`, with current language-generation identifier `0.1`, absence meaning no package-declared restriction and presence requiring exact equality with the active resolution context. The active language compatibility identifier is itself resolution input. Complete deterministic root/member semantic assembly is now owned without raw TOML, absolute host paths, member lockfiles or filesystem traversal order.
- Keep `TOOL001-F2B3` dependency-blocked after F2B2: the semantic input model is complete, but the repository does not yet expose an explicitly owned suitable hashing capability. Digest/stale implementation must not add a Package-Tool-private JVM/host crypto shortcut merely to close F2.

- Close `I023-B2C` with Protos-source conformance for D044 synchronous control transfer. Exact Error objects signaled by either a reached condition or body cross `while` unchanged to the surrounding handler; non-local `^` from either callback likewise reaches the pre-existing lexical return home and preserves exact returned-object identity. Callback counters prove effects before each transfer remain visible while no body, next condition, or post-loop effect executes after the transfer.
- `I023-B2D` becomes READY for the remaining normal-Future body-result and structured-ownership closure. This slice changes conformance/project state only: no runtime, normative specification, native boundary, license terms, or implementation version change.

- Formalize `DIST001 — end-user distribution and release engineering` and close documentation-only `DIST001-C` with the selected release policy: implementation `-SNAPSHOT` revisions are traceability points rather than automatic releases; CI snapshot artifacts remain distinct from GitHub Releases; public releases are explicitly selected milestone revisions and normally pre-releases while Core v0.1 remains draft; each distribution must declare and validate its GraalVM/JDK runtime contract. DIST001-A is READY. No binary artifact, tag, GitHub Release, specification, implementation-version, or license-term change.
- Close `TOOL002-E1B — confined Package/TOML corpus authority`. The bundled Test Tool now receives two independent bootstrap-local standard `Filesystem` capabilities: existing `filesystem` remains rooted exactly at `protos/tests/conformance`, while `packageTomlFilesystem` is rooted exactly at `protos/tests/package-tool/toml-syntax`. `Main.protos` constructs the E1A Package/TOML TestPlan through `Manifest.loadPackageToml(packageTomlFilesystem)` but deliberately does not execute that plan.
- Keep the host boundary mechanical: `ProtosCli` generalizes bundled-tool Filesystem installation to an explicit bootstrap slot name and provisions the existing read-only tree-confined backend twice. A Protos-owned fixture proves that the same relative `manifest.tsv` path resolves independently through the two capabilities and yields the expected distinct canonical plans. No Package Tool `self:*` fixture resolution, Package fixture execution, expectation-policy change, new Filesystem constructor, or normative language change is introduced; `TOOL002-E2A` becomes READY.

## 0.2.217-SNAPSHOT

- Close documentation/design slice `TOOL001-F2B1 — per-manifest semantic resolution-input projection design` without changing the implementation version. Freeze which manifest-v1 values affect resolution, which values are excluded, and which semantic owner must normalize each included value before stale hashing. D2 dependency constraints contribute their parsed semantic kind/bounds rather than retained source `.text`; raw TOML formatting is never digest input.
- Require future `protos-resolution-input-v1` construction to fail closed when a resolver-affecting value lacks a canonical semantic owner. Workspace member/path interpretation, canonical path-dependency identity, language/package compatibility and complete root/member assembly remain unresolved at this checkpoint, so F2B1 does not implement a digest or stale comparison. `TOOL001-F2B2` becomes READY for that root/workspace semantic assembly design; F2B3 digest/stale comparison remains dependency-gated.

- Start `TOOL001-F2 — physical lock integration` with `TOOL001-F2A — confined protos.lock read/publish substrate`. Add ordinary bundled-Protos `self:LockFile`: `load(filesystem)` reads exactly `protos.lock` through the already-provisioned confined Filesystem, decodes the complete UTF-8 stream and delegates canonical validation/model construction to closed F1C `LockDocument.parse`; `publish(filesystem, model)` first canonicalizes/structurally validates through `LockDocument.write`, then publishes through the closed B2 `MetadataPublication` transaction using `.protos.lock.stage -> protos.lock`.
- Preserve capability and update boundaries: F2A adds no host privilege, no new CLI command/dispatch, no resolver/candidate discovery, no workspace traversal, no PackageId generation, no ContentIdentity hashing, no registry/network/store behavior and no normal-execution rewrite. It also does not claim stale detection: exact `protos-resolution-input-v1` semantic digest construction remains a separate design/implementation prerequisite.
- Add Protos-owned lock-file fixtures plus a Java host-boundary harness for the confined Filesystem only. Conformance covers canonical load, non-canonical rejection, ordinary missing-file IOError, canonical publication, validation before stage creation and createNew stage-collision preservation.

## 0.2.216-SNAPSHOT

- Close `I023-B2B` with Protos-source conformance for D044 callback-activation timing. The condition and body are activated with an empty supplied-argument vector only when their semantic step is reached; ordinary left-to-right parameter binding therefore runs defaults at activation time, preserves an earlier default effect before a later missing-required failure, and never inspects or binds an unreachable body. A reached invalid body fails only after the true condition, while default-only condition/body closures execute normally under the same zero-argument activation path.
- Reconcile the four I023-B2A conformance sources with the repository-required APL Part 5 source notice after the previous slice omitted those headers. This changes notice text only and does not change APL terms. `I023-B2C` becomes READY; I023-B2 and I023-B remain IN_PROGRESS. No runtime, normative specification, native boundary, license terms, or implementation version change.

- Close `TOOL002-E1A — Package/TOML manifest planning`. Bundled `Manifest.protos` now consumes the retained two-column Package Tool TOML manifest shape as inert planning policy: safe relative fixture paths receive stable `package-tool/toml-syntax/...` CaseIds, retained `true` rows normalize to the already-owned `boolean`/`true` CaseSpec form, and retained `error` rows normalize to generic `error` without executing a fixture. Unknown expectations, wrong column counts and unsafe paths fail closed in Protos policy.
- Factor the manifest reader into one parser-parameterized bounded loader while preserving the existing three-column conformance `load(filesystem)` contract exactly. Add a Protos-owned planning fixture with Java used only to provision a temporary confined read-only Filesystem and execute the fixture harness. Formalize E1B/E2A/E2B/E3/E4 as dependency-ordered follow-up slices; E1A adds no Package Tool resolver, no second corpus authority to the public Test Tool, no test execution/cutover, and no normative specification change.

## 0.2.215-SNAPSHOT

- Close `TOOL001-F1C3 — total writer + canonical rejection + F1C closure` and parent `TOOL001-F1C`. Extend pure bundled-Protos `self:LockDocument` with the F1B3 canonical total ordering and writer: workspace members by canonical qstring bytes then PackageId qstring bytes; registry nodes by PackageId qstring bytes then D1 ReleaseVersion precedence; Git nodes by PackageId/revision qstring bytes; and dependency records by source-kind-ranked declaring ref (`workspace < registry < git`), alias qstring bytes and target ref.
- Preserve the F1C2 structural boundary as `parseStructural(text)`, while public `parse(text)` now requires exact canonical bytes by `parseStructural -> write -> byte-identical String equality`. The writer emits the one F1A header separator, root first, then workspace/registry/Git/dependency classes, one LF per body record, and no extra blank/final records. Structurally valid but non-canonical class/order input therefore fails closed.
- Add cross-slice Protos conformance for canonical root/full-document round trips, class normalization, semantic registry-version ordering (`1.2.0` before `1.10.0`), workspace/Git/dependency total ordering and canonical rejection. The existing Java harness remains unchanged. F1C is complete; TOOL001-F remains IN_PROGRESS and this slice deliberately does not allocate a new F continuation before a fresh post-F1C audit.

## 0.2.214-SNAPSHOT

- Add community entry points for adoption and external contribution: a human-facing roadmap, Code of Conduct, support and security policies, GitHub Issue Forms, and a pull-request template. Route questions, exploratory ideas, and design discussion to the now-enabled GitHub Discussions space, while keeping Issues focused on reproducible bugs and scoped actionable work. No specification, implementation-version, or license-term change.
- Close `I023-B2A` with adversarial Protos-source conformance for representative normal non-Boolean D044 condition results: `null`, Integer, ordinary `Object`, and Future all signal a fresh generic standard Error before any body activation. Two independent invalid-null failures prove fresh Error identity and direct `Error` parentage; the Future case proves `while` does not await or adopt a Future condition result.
- Subdivide the remaining synchronous I023-B2 evidence into B2B callback-activation timing, B2C Error/non-local-return transfer, and B2D body-Future/structured-ownership closure. This slice changes conformance/project state only: no runtime, normative specification, native boundary, license terms, or implementation version changes.

- Close `TOOL001-F1C2 — body record/model + structural validation`. Add ordinary bundled-Protos `self:LockDocument` over the closed F1A/F1B/F1C1 grammar. It parses complete in-memory lock-format-1 documents into explicit root/workspace-member/registry-node/git-node/dependency arrays, preserving opaque locator/authority/fetch/content fields without inventing PackageId, registry, Git or content-hashing policy.
- Reject structurally invalid graphs: missing/multiple roots; duplicate workspace declarations or workspace node identities; the root repeated as a member; duplicate registry/Git node references; duplicate `(declaring-ref, alias)` edges; dangling declaring/target references; blank body records; unknown/malformed record keywords/fields; and invalid lexical content delegated to F1C1. Record class/order canonicality is intentionally not rejected here: F1C3 owns total sorting/writing and whole-document parse-write equality.
- Reuse the existing F1C1 Java harness unchanged; all new expectations remain Protos fixtures. `TOOL001-F1C3 — total writer + canonical rejection + F1C closure` becomes READY. Filesystem I/O, resolution/discovery, PackageId generation, AuthorityIdentity authentication, ContentIdentity tree hashing, registry/network/store and update behavior remain outside F1C.

## 0.2.213-SNAPSHOT

- Add `CONTRIBUTING.md` and a public AI-assisted development policy. Explicitly welcome AI-assisted and fully AI-generated contributions while holding them to the same specification, review, validation, and contributor-responsibility standards as manually written work; require only a brief material-AI disclosure rather than prompts or model logs. Link the policy from the README and align repository agents with the same contribution rules.
- Start the executable `TOOL001-F1C — canonical lock parser/writer + round-trip conformance` parent with `TOOL001-F1C1 — lock lexical primitives`. Add ordinary bundled-Protos `self:LockSyntax` implementing the already-frozen lock-format-1 in-memory lexical layer: strict one-space line tokenization, canonical F1B1 qstring parse/render, canonical F1A header parse/render, and typed workspace/registry/git node-reference parse/render. Registry refs delegate ReleaseVersion validation to the closed D1 owner; opaque PackageId/Git revision text is not redefined here.
- Keep F1C1 bounded below body graph semantics. It does not parse root/workspace/external/dependency records, validate graph references/uniqueness, sort whole documents, access Filesystem, resolve dependencies, discover packages, authenticate authorities, hash package content, or use registry/network/store capabilities. Formalize F1C2 for body record/model/structural validation and F1C3 for total writer/canonical rejection/round-trip closure.
- Add a minimal Java execution harness only for provisioning the existing bundled Package Tool module resolver; all lexical/header/reference expectations and error cases live in Protos fixtures. The lexer is state-machine based over Bytes iteration with a per-octet state snapshot, preventing a state transition from being reinterpreted by another branch during the same octet; it avoids recursive one-call-per-octet scanning.

## 0.2.212-SNAPSHOT

- Add a non-normative licensing rationale explaining why APL-1.0 was chosen, with emphasis on welcoming open-source, commercial and proprietary applications, independent extensions and private internal use while preserving reciprocity for the Protos Licensed Work. Link the rationale from the README. No license terms, specification semantics or implementation version change.
- Close `I023-B1` after the post-I023-A longevity audit exposed iteration-proportional replay retention. Replace the accumulated completed-callback prefix with one stable per-`while` callback checkpoint: every normally completed condition/body callback commits and discards its evaluator child suffix plus completed callback activation keys, then reuses the same callback ordinal while retaining the enclosing native `while` activation. A callback that suspends remains uncommitted and replayable, so D044 observable semantics are unchanged while retained state becomes independent of completed iteration count.
- Remove `whileCompletedInvocations` and the resulting host-`int` overflow path from the dynamic frame. Add a Java runtime-invariant regression (Java intentionally, because it measures private evaluator retention rather than Protos language behavior) comparing replay event/activation counts after 8 versus 4096 completed iterations before a forced suspension. Subdivide I023-B into closed B1 and READY B2; the Core native boundary remains unchanged.

## 0.2.211-SNAPSHOT

- Close `TOOL002-D4` and parent `TOOL002-D`. Migrate retained `closure-error-parent-fresh` without transferring Closure authority: bundled Protos evaluates the retained source once inside one fresh child Process, invokes that exact candidate twice through ordinary `Error.handle`, checks both immediate Error parents plus distinct Error identity in the child identity domain, and returns only canonical Boolean evidence through the existing D1 detached observation.
- Complete the D ownership cutover. A full-corpus Protos fixture now requires every non-Future main-manifest row to be selected and pass through the bundled Test Tool and requires every skipped row to be one of the explicitly deferred `future-*` families. The legacy `ProtosLanguageConformanceTest` removes direct policy for boolean/null/integer/fixed/Float/Error/closure-fresh families and now executes only `future-*` pending TOOL002-F. TOOL002-E becomes READY. No runtime/D1 boundary or normative specification change.

## 0.2.210-SNAPSHOT

- Publish `I023-A` for D044 standard `Object.while`: add the ordinary Closure-specific selector with eager semantic Closure receiver/body validation, exact one-argument contract, zero-argument pre-test condition/body activation, strict canonical `true`/`false` decisions, ignored body results, and canonical `null` normal completion. Zero and repeated iterations are covered by Protos-source conformance; invalid receiver/body/arity cases fail through the standard generic Error path.
- Reuse the I022 replay-stable task-local control substrate for iterative callback replay. A `WHILE` frame records only the next semantic phase plus the completed direct-invocation prefix/cursor, and the evaluator tape can skip that prefix in O(1) on resume. This prevents completed condition/body callbacks from being invoked again merely to reconstruct the host stack, without adding a language-visible frame, scheduler checkpoint, Future, cleanup scope, truthiness rule, or alternate invocation model.
- Reconcile the audited Core native boundary for the one host-irreducible control primitive: `ProtosStandardObjectProtocol` grows from four to five native Closure construction sites and the Core total from 110 to 111 across the same 30 providers. `I023-A` closes, `I023-B` becomes READY, while B007 remains READY until final I023 closure.

## 0.2.209-SNAPSHOT

- Close `TOOL001-F1B3 — external node blocks + dependency edges + F1B closure` and parent `TOOL001-F1B`. Freeze lock-format-1 external records as flat single-line `registry-node` and `git-node` records over the typed F1B1 node references. Immutable registry nodes carry diagnostic/retrieval-stable `locator`, opaque `AuthorityIdentity`, and mandatory `ContentIdentity`; Git nodes carry credential-free portable `fetch` provenance and mandatory `ContentIdentity`. PackageId/version/revision are not duplicated because they already live in the exact typed node reference.
- Freeze exact dependency records as `dependency <declaring-node-ref> alias <qstring> target <node-ref>`. The complete v1 body is line-oriented with no blank lines after the F1A header separator: root, workspace members, registry nodes, Git nodes, then dependency records, all under deterministic total ordering. PackageLocator remains non-identifying; its registry-node copy is diagnostic/retrieval metadata and may remain at an older permanent alias after rename. `ArtifactDigest` is deliberately omitted from core lock-format 1; transport metadata stays registry/store-owned.
- Reconcile the lock-format audit now that F1A/F1B1/F1B2/F1B3 freeze the complete canonical v1 grammar. `TOOL001-F1C — canonical lock parser/writer + round-trip conformance` becomes READY as a pure bundled-Protos in-memory slice; Filesystem publication, graph resolution, candidate discovery, ContentIdentity tree canonicalization, registry/network/store/workspace policy and update operations remain separate. No implementation-version change is made.

- Close `TOOL002-D3C2C`, D3C2, D3C and D3: activate retained `float-bits` expectations in the bundled sequential Test Tool. D3C2A parses the exact 16-hex raw pattern, D3C2B reconstructs its portable non-NaN binary64 Float, and D3C2C requires a completed/null-error observation whose detached value matches that Float by primitive `===`, preserving signed zero and Float-family identity.
- Migrate every historical D3 test that used `float-bits` solely as an unsupported sentinel to TOOL002-F-owned `future-integer`. Reconcile the D3C2A parser and D3C2B mechanism regression tests so they assert those mechanisms remain available after activation rather than incorrectly requiring `float-bits` to stay unsupported. Add Protos-owned exact/mismatch, detached signed-zero/one-third, raw-NaN rejection and sequential selection coverage. D4 becomes READY; no runtime/D1 or normative change.

## 0.2.208-SNAPSHOT

- Close `TOOL001-F1B2 — root/workspace representation`. Lock-format 1 now represents the resolution root with exactly one `root workspace <PackageId>` record using the F1B1 typed workspace reference. A manifest-v1 workspace does not acquire a second virtual identity: the root remains the package described by the root manifest, and each additional `workspace.members` declaration is recorded as `workspace-member <declared-member-string> workspace <PackageId>`. The member string is preserved as an opaque F1B1 qstring; path interpretation and membership validation remain workspace-policy concerns.
- Freeze root-section cardinality/order only: exactly one root record, followed by zero or more workspace-member records sorted by canonical qstring bytes of the declared member string and then PackageId qstring bytes as a defensive tie-breaker. The root package itself is not redundantly emitted as a member. F1B2 does not choose external registry/git blocks, dependency-edge syntax, content/provenance fields, or final inter-block blank-line policy. `TOOL001-F1B3` becomes READY for those remaining body decisions and F1B closure. No implementation-version change is made.

- Subdivide `TOOL001-F1B — canonical lock body node/edge grammar` and close `TOOL001-F1B1 — canonical scalar strings and typed node references`. Freeze one deterministic quoted-string representation for body scalar values and identity-derived node references as typed tuples (`registry`, `git`, `workspace`) instead of delimiter-composed strings such as `pkg:<PackageId>@<version>`. This deliberately avoids coupling lock-format 1 to the still-open public textual encoding of `PackageId`.
- Keep F1B1 below body structure: it does not select root/workspace records, node blocks, field ordering, dependency-edge lines, locator/fetch/content field requirements, parser/writer behavior or PackageId's public encoding. `TOOL001-F1B2` becomes READY for root/workspace representation; F1B3 remains dependency-blocked for registry/git node blocks, fields, edges and final F1B closure. No implementation version change is made.

- Record a non-committing Package Tool architecture note for future reusable-library extraction. The schema-neutral TOML front-end (`TomlSyntax` plus generic `TomlDocument`) is a candidate for a reusable TOML library while `ManifestSchemaV1` stays package-specific. The generic Semantic Versioning parse/precedence core behind `ReleaseVersion` is also a candidate, while the Package Tool retains its restricted release-value contract (including no build metadata), dependency constraint semantics and selection/resolution policy unless independent use cases justify broader extraction. This note creates no `LIBxxx`/`TOOLxxx` item and does not block TOOL001-F.

- Start `TOOL001-F — canonical physical lockfile v1` with the deliberately small documentation/design prerequisite `TOOL001-F1A — lock header grammar`. Freeze only the exact canonical three-line header shape for `protos.lock`: `lock-format <decimal>`, `resolver-version <decimal>`, and `resolution-input <method-token> <algorithm-token>:<lowercase-hex>`, followed by exactly one empty line before body records. Header keywords use exact ASCII spelling, fields are separated by one ASCII space, decimals are canonical unsigned decimal, tokens are lowercase ASCII kebab tokens, and header lines never wrap.
- Keep the scope boundary explicit: F1A does not choose package/root/dependency body punctuation, node-key encoding, string escaping, diagnostic locator emission, artifact-digest fields, VCS/workspace records, parser/writer implementation, graph resolution or network/store behavior. `TOOL001-F1B` becomes READY for body node/edge grammar design. No implementation version change is made because this slice changes design/project governance only.

- Publish `TOOL002-D3C2B`: establish exact portable binary64 reconstruction/comparison entirely in bundled Protos on top of the D3C2A 16-hex parser. Sign/exponent/fraction are decomposed with exact Integer arithmetic; finite/subnormal/zero values use an exact at-most-53-bit `Float(Integer)` significand scaled by a logarithmically constructed exact power of two, infinities use ordinary IEEE division, and sign is applied through source-backed `Float.negated()`.
- Match reconstructed non-NaN values through primitive `===`, which preserves exact Float raw-binary64 identity including `+0.0` versus `-0.0`. Reject exponent-all-ones/nonzero-fraction raw patterns so NaN payload/sign bits remain non-portable and owned by `float-nan`. D3C2B deliberately leaves `float-bits` unsupported by the sequential runner; D3C2C becomes READY. No runtime/D1 or normative change.

## 0.2.207-SNAPSHOT

- Close `TOOL001-E2 — retained exact-version preference` and parent `TOOL001-E — local/offline version selection policy`. Add ordinary bundled-Protos `self:RetainedVersionSelection`: when an exact retained ReleaseVersion remains present among the already-known candidates and still satisfies the current closed D2 constraint/prerelease policy, preserve it even if E1 could choose a newer satisfying version; otherwise fall back to E1 fresh highest-satisfying selection.
- Keep E2 intentionally version-level and lockfile-independent. `null` means no retained version and immediately delegates to E1. A retained version that disappeared or became ineligible does not block progress; fresh selection applies. Invalid retained/candidate text fails through the existing strict ReleaseVersion parser, and no-match still fails through E1. E2 does not parse `protos.lock`, validate package/content identity, decide yank/trust/language compatibility, resolve graph closure, discover candidates or perform registry/network/store/workspace/update operations.
- Extend the existing Protos-owned package-version corpus with retained-version dominance over newer releases, missing/ineligible fallback, null-retention fresh selection, retained prerelease preservation, prerelease invalidation, exact constraints and fail-closed invalid/no-fallback cases. Final E reconciliation closes the bounded pure local version-selection parent while TOOL001 remains open for separately scoped resolver/lock work.

## 0.2.206-SNAPSHOT

- Start `TOOL001-E — local/offline version selection policy` with the bounded `TOOL001-E1 — fresh version selection` slice. Add ordinary bundled-Protos `self:FreshVersionSelection`, which receives already-known ReleaseVersion candidates, filters them only through the closed D2 DependencyConstraint policy and chooses the highest satisfying candidate by D1 SemVer precedence.
- Keep E1 intentionally below full resolver/candidate eligibility. It does not discover candidates, interpret PackageId/authority metadata, consult yank/trust/language compatibility, fetch registry/network data, inspect transitive dependency closure, preserve a lock selection, serialize `protos.lock`, or perform workspace/store/update operations. An empty/no-match candidate set fails closed so later resolver layers can own contextual dependency-path diagnostics instead of silently manufacturing absence.
- Extend the existing Protos-owned package-version corpus with exact/caret/interval selection, explicit-prerelease admission, order independence and fail-closed empty/no-match/invalid-candidate cases. `TOOL001-E1` closes and `TOOL001-E2` retained exact-version preference becomes READY.

## 0.2.205-SNAPSHOT

- Publish `TOOL002-D3C2A`: bundled Protos now parses retained `float-bits` payloads as exactly sixteen hexadecimal digits into an exact unbounded Integer raw pattern in `0..2^64-1`. Both upper- and lower-case hex digits are accepted; wrong length or any non-hex octet fails closed as Test Tool policy.
- This slice deliberately does not activate `float-bits` in `isSimpleExpectation`, execute such cases, construct Floats, compare binary64 values, or change runtime/D1. D3C2B owns the exact binary64 construction/comparison mechanism; D3C2C owns final runner integration.

## 0.2.204-SNAPSHOT

- Close `TOOL001-D2D — prerelease admission + D2 closure` and parent `TOOL001-D`. Complete ordinary bundled-Protos dependency-constraint v1 so stable requirements never admit prerelease candidates implicitly; a prerelease candidate is eligible only when the exact/caret/interval constraint explicitly names a prerelease with the same MAJOR.MINOR.PATCH tuple, after which ordinary exact/range precedence still decides satisfaction.
- Preserve the selected intent boundary across forms: `^2.0.0-rc.1` admits later `2.0.0` prereleases, final `2.0.0`, and later stable releases within the caret range but rejects unrelated future-tuple prereleases; `>=2.0.0-rc.1 <2.0.0` admits the named tuple's later prereleases while still excluding final `2.0.0`; explicit prerelease upper bounds likewise admit only that bound tuple. `^0.0.0-rc.1` admits its release-candidate train and final `0.0.0` while retaining the D2B exact-zero stable boundary.
- Promote the four D2B/D2C deferred prerelease fixtures into final boolean conformance, add cross-form Protos regressions, reconcile the version-resolution implementation checkpoint and close `TOOL001-D2`/`TOOL001-D`. Candidate selection, graph resolution, lock preservation/serialization, workspace/store, registry/network, yank and update policy remain outside this closed pure-value parent.

## 0.2.203-SNAPSHOT

- Start the subdivided `LIB004-A — Files.readAllBytes` implementation with `LIB004-A1`. Publish exact-case `std:io/Files` as ordinary Protos Standard Library source with only `readAllBytes(filesystem, path)` in this slice: explicit authority, one retained open Future, `ensure`-owned File custody/close, finite 16×65536 read windows, one fresh open whole-result Bytes, and no filesystem snapshot/reopen/probe semantics.
- Keep Core and the native boundary unchanged. Bytes allocation uses the existing source-level `Encoding.UTF8.encode("")` empty-Bytes path because Bytes is deliberately not a required public prelude binding; the operation remains binary and exposes no Encoding parameter/default. Add Protos-owned empty/freshness, multi-window ordering and open-failure conformance with Java restricted to standard-module/Filesystem fixture provisioning. A2/A3 retain the adversarial cancellation/late-open/close-precedence evidence before parent LIB004-A can close.

## 0.2.202-SNAPSHOT

- Close `TOOL001-D2C — explicit bounded intervals`. Extend ordinary bundled-Protos `self:DependencyConstraint` with exactly two whitespace-joined primitive comparisons over full ReleaseVersion values, requiring one lower (`>`/`>=`) and one upper (`<`/`<=`) bound. Conjunction order is irrelevant; stable candidates are evaluated by D1 precedence with exact inclusive/exclusive endpoints.
- Keep D2C deliberately bounded: open-ended requirements, extra comparisons and same-side pairs fail closed. Bounds may parse prerelease ReleaseVersions, but any interval satisfaction involving a prerelease bound/candidate remains fail-closed until D2D owns the cross-form prerelease-admission policy. Candidate selection, resolver, lockfile, workspace, store and registry/network behavior remain outside D2.
- Promote the D2A deferred interval fixture into a successful parse fixture and extend the existing Protos-owned package-version corpus with boundary, reversed-order, tab-separator, prerelease-parse and malformed/deferred-prerelease cases. `TOOL001-D2C` closes and `TOOL001-D2D` becomes READY.

## 0.2.201-SNAPSHOT

- Publish `TOOL002-D3C1`: bundled Protos now owns retained `float-nan` expectation policy. The expected payload must be exactly `-`; the Test Tool constructs the Core semantic Float NaN with ordinary `0.0 / 0.0` arithmetic and compares the detached completed value by primitive `===`, preserving Float-family membership while remaining independent of host NaN payload/sign representation.
- Add Protos-owned policy, mismatch, detached fresh-Process and sequential-runner focal coverage. Finite/infinite Floats and non-Float values do not match; malformed payload fails closed. `float-bits` remains skipped before source access and is isolated as TOOL002-D3C2, which becomes READY. No runtime/D1 boundary or normative specification change.

## 0.2.200-SNAPSHOT

- Close `TOOL001-D2B — caret dependency constraints`. Extend ordinary bundled-Protos `self:DependencyConstraint` with caret parsing/bound construction and stable-candidate satisfaction: `^1.4.2` is `>=1.4.2 <2.0.0`, `^0.4.2` is `<0.5.0`, `^0.0.7` is `<0.0.8`, and `^0.0.0` is exact.
- Preserve the deliberate D2 subdivision: D2B may parse a prerelease lower bound but satisfaction involving prerelease constraints/candidates fails closed until D2D owns the cross-form prerelease-admission rule. Explicit bounded intervals remain D2C; candidate selection/resolver/lockfile/network/store policy remains outside D2.
- Extend the existing Protos-owned package-version corpus with caret major/zero-major bounds, lower-inclusive/upper-exclusive stable satisfaction, exact-zero behavior, prerelease-bound parsing and fail-closed deferred prerelease satisfaction. `TOOL001-D2B` closes and `TOOL001-D2C` becomes READY.

## 0.2.199-SNAPSHOT

- Close `TOOL002-D3B2B` and parent `TOOL002-D3B`: bundled `Runner.evaluateSimple` now owns retained `error-parent` expectations entirely in Protos, resolving only the closed Core v0.1 standard Error prototype taxonomy and requiring FAILED state, canonical null value, a detached Error, and primitive-identity equality between `observation.error.parent()` and the named expected prototype.
- Preserve immediate-parent semantics rather than ancestry: an Error occurrence parented by `EncodingError` does not satisfy expected `IOError`. Normal category mismatches remain inert `passed=false` evidence, while an unknown expected Error prototype fails closed as Test Tool policy. Real D1 fresh-Process focal cases prove detached `SlotNotFound` and generic root-`Object.parent()` failures retain their standard immediate parent. The sequential runner now selects `error-parent`; the old D3B1 skip fixture moves to `float-bits`, which remains D3C-owned. D3C becomes READY.

## 0.2.198-SNAPSHOT

- Start the subdivided `TOOL001-D2 — dependency constraint language v1` with `TOOL001-D2A — exact constraints`. Add ordinary bundled-Protos `self:DependencyConstraint` with only bare full `ReleaseVersion` parsing and exact satisfaction; a bare `1.4.2` means exactly `1.4.2`, and an explicitly named prerelease means exactly that prerelease.
- Keep the slice boundary strict: caret constraints remain D2B, explicit bounded intervals remain D2C, and general prerelease-admission/cross-form conformance remains D2D. D2A adds no candidate selection, resolver, lockfile, workspace, package-store, registry/network or Core behavior.
- Extend the existing Protos-owned package-version corpus with exact parse/match/mismatch/prerelease cases and fail-closed deferred syntax checks. `TOOL001-D2A` closes, `TOOL001-D2B` becomes READY, and parent D2 remains IN_PROGRESS.

## 0.2.197-SNAPSHOT

- Start `TOOL001-D — release-version and dependency-constraint value policy` with the bounded `TOOL001-D1 — ReleaseVersion` slice. Add ordinary bundled-Protos `self:ReleaseVersion` with strict `MAJOR.MINOR.PATCH[-PRERELEASE]` parsing, no build metadata, canonical decimal core identifiers, SemVer prerelease identifier validation and exact precedence comparison.
- Preserve the package architecture boundary: D1 implements only the already-selected `ReleaseVersion` value/ordering contract. It does not parse dependency constraints, apply caret/interval/prerelease eligibility, choose candidates, read/write `protos.lock`, resolve dependencies, interpret PackageId/locator/authority strings, access network/store state or change Core semantics.
- Add Protos-owned conformance for core/prerelease structure, arbitrary-size Integer components, the canonical SemVer precedence chain, ASCII/numeric prerelease ordering and strict rejection of prefixes, missing components, leading zeroes, build metadata, malformed prerelease identifiers, whitespace and non-ASCII prerelease syntax. `TOOL001-D1` closes and `TOOL001-D2` becomes READY.

## 0.2.196-SNAPSHOT

- Close `TOOL001-C7` and the bounded historical package-tool manifest Slice 3 parent `TOOL001-C` as documentation/governance-only final reconciliation over the already-published C1-C6 implementation. Final cross-slice validation re-runs the bundled-tool/bootstrap, metadata publication, TOML/schema and manifest-command focal suites plus the complete Maven suite; no production, test, Protos source, normative specification or implementation-version change is introduced.
- Reconcile the Package Tool architecture and schema-v1 records with published reality: exact bundled tool entry selection is host bootstrap mechanism, package policy remains ordinary bundled Protos code, `protos package manifest` reads exactly confined `protos.toml`, consumes UTF-8 to EOF, validates the closed structural schema and emits Protos-owned diagnostics. Resolution/version constraints, lockfile policy, workspace interpretation, package-store/archive work, registry/networking and publication remain separate future TOOL001 scopes rather than implicit continuation of manifest Slice 3.

- Publish `TOOL002-D3B2A`, the general Core prerequisites discovered by the `error-parent` migration: implement the already-normative inherited `Object.parent()` reflection message and restore the normative standard prelude `Object` binding instead of adding a Test-only parent inspection escape hatch. The zero-argument operation reports the receiver's exact immutable immediate delegation parent, or canonical `null` only for the unique root `Object`, across ordinary and represented values.
- Centralize semantic immediate-parent projection in `ProtosValueLookup` so ordinary lookup and reflection use the same representation boundary. Add Protos conformance for ordinary/custom parents, root, Error taxonomy, Integer/Float/String/Boolean/null represented values, and arity failure. The audited Core native boundary grows by one reviewed representation-bridge construction site in the existing Object provider. `error-parent` policy itself remains D3B2B READY.

## 0.2.195-SNAPSHOT

- Close `TOOL001-C6 — confined project manifest read/diagnostics` (legacy manifest Slice 3D). Add ordinary-Protos `self:ManifestCommand`, reading exactly `protos.toml` through the already-provisioned confined Filesystem, decoding complete UTF-8 text across progress-oriented `TextReader.readText()` chunks, and invoking the closed `ManifestSchemaV1.parse` pipeline.
- Add exact bundled `ManifestMain` for `protos package manifest`. The host driver performs only mechanical exact-entry selection, while read/validation/diagnostic policy stays in Protos; the historical bare `Main.protos` entry remains byte-for-byte unchanged. Successful validation reports `protos.toml: valid schema v1`; read/UTF-8 failures and TOML/schema failures receive distinct Protos-owned diagnostics before the exact Error continues to the common tool boundary.
- Add Protos-source integrated cases for valid, invalid-schema, missing, invalid-UTF8 and multi-chunk (>8 KiB) manifests, with Java restricted to confined-Filesystem/driver integration. C6 closes and `TOOL001-C7` becomes READY. No dependency resolution, `protos.lock` read, network access, metadata mutation, package store or new Core/native boundary is introduced.

# Changelog

## 0.2.194-SNAPSHOT

- Close I022-F and the parent I022 with adversarial cross-feature evidence rather than another production control mechanism. New Protos conformance composes selected-handler deactivation with cleanup-initiated cancellation, cancellation with cleanup Error recovery, cleanup Error/non-local-return precedence with structured children, and task-local handler isolation across child Futures. Add an Actor-termination focal showing that cancellation cleanup may suspend while the Actor remains TERMINATING and that terminal Actor completion waits for the cleanup-owned task to finish.
- Re-audit the Core native boundary at I022 closure. The executable architecture guard remains exactly 109 `nativeClosure` construction sites across 30 Core providers; I022-D/E/F add none. Reconcile stale non-Core explanatory references in `CORE_NATIVE_BOUNDARY.md` to that definitive count while retaining older numerical entries only as historical progression at their named slices.
- Mark I022 and I022-F CLOSED without changing the implementation version: this is test/project/native-inventory closure over the already-published I022-A..E runtime. Clear LIB004's I022 dependency, but move LIB004 only to OPEN—not READY—because its persisted design record is still a non-normative DRAFT with no approved implementation surface. Finalize that design before implementation begins.

- Close `TOOL001-C5 — manifest schema v1` with `TOOL001-C5D` (legacy manifest Slice 3C3). Complete ordinary-Protos `self:ManifestSchemaV1` with `fromTable(root)` / `parse(text)`, preserving the published C5B mandatory base and C5C optional-section helpers while adding the final `dependencies` Map to the ordinary ManifestV1 data shape.
- Validate each dependency alias declaration as exactly one schema-v1 source form: registry requires non-empty String `authority`/`package`/`version`; Git requires non-empty String `git`/`rev`; path requires one non-empty String `path`. Mixed, incomplete, empty, non-table, array-of-tables and unknown-field declarations fail closed. Alias spelling and all decoded Strings are preserved exactly; no alias grammar, URL/revision validation, version/constraint semantics, path interpretation, network, resolution or lock policy is introduced.
- Add Protos-owned final-schema conformance for absent/empty dependencies, all three expanded forms, inline-table equivalence, malformed/mixed/incomplete declarations and cross-section failure propagation. `TOOL001-C5D` and parent C5 close; `TOOL001-C6` becomes READY for the separate confined `protos.toml` read/UTF-8/diagnostic integration.

## 0.2.193-SNAPSHOT

- Subdivide `TOOL002-D3B` into D3B1 fixed-width policy and D3B2 Error-parent policy, and close only D3B1. `Runner.evaluateSimple` now accepts `fixed-integer`, parses retained `FAMILY:value` syntax in Protos, selects one of the eight Core fixed-width factories, and matches through primitive `===` so numeric family and exact value are both preserved.
- Keep `error-parent` unsupported until D3B2. Focals cover all eight families, family/value mismatches, malformed/unknown/out-of-range policy, and runner selection that still skips D3B2 rows before source access. D3B2 becomes READY; D3C remains dependent.

## 0.2.192-SNAPSHOT

- Close `TOOL002-D3A3`: compose the D2 TestPlan, D3A1 complete source loading and D3A2 simple expectation policy into the initial sequential bundled-Protos runner. `Array.each` only constructs a dependency chain; each selected case runs in a `Future.then` continuation after the previous selected case resolves, avoiding one recursive Protos frame per case while retaining manifest order and fresh-Process-per-case execution through D1.
- Skip expectation kinds not yet owned by D3A without reading or executing their source, aggregate each selected CaseSpec plus its complete D3A2 evidence into frozen ordered run results, and preserve normal mismatches as data so later cases still run. Tool/source-policy failures still fail the chain closed. `Main.protos` now runs the supported subset while preserving its existing bootstrap stdout; reporting/exit-status policy remains later TOOL002 work. D3B becomes READY.

## 0.2.191-SNAPSHOT

- Close I022-E3 and the parent I022-E cancellation-unwind slice. Preserve the E1 invariant that only `REQUESTED` is pending for observation: once cancellation reaches `UNWINDING`, `ensure` cleanup can use ordinary Future suspension/replay without the same request being delivered again, and the task-backed producer Future remains PENDING until cleanup and required structured drain finish.
- Make cleanup precede structured cancellation drain when cancellation is first observed inside an active ensure extent. Pre-existing children still receive cancellation, but their presence no longer moves the parent to child-drain suspension before cleanup can run. At successful post-cleanup cutover, cancel every child still structurally owned, including cleanup-created unawaited children, and publish terminal CANCELLED only after that drain. Cleanup-created children explicitly awaited by cleanup may run to ordinary completion while the already-honored parent request stays shielded.
- Add Protos-source conformance for suspended cleanup exact-once progress under a repeated `cancel()` call, suspended cleanup Error supersession with exact identity, awaited cleanup-created child progress, unawaited cleanup-created child cancellation/drain, and pre-existing-child ordering. Add one Java-side focal only for the runtime-internal intermediate assertion that the producer Future is still PENDING while cancellation cleanup is suspended. No specification or native-Closure boundary change. I022-E is CLOSED and I022-F becomes READY for final adversarial cross-feature closure.

## 0.2.190-SNAPSHOT

- Close `TOOL001-C5C — optional manifest schema sections` (legacy manifest Slice 3C2). Extend ordinary-Protos `self:ManifestSchemaV1` with `sectionsFromTable(root)` / `parseSections(text)` over the published C5B mandatory base. The partial schema model now validates and materializes optional `compatibility`, `exports`, and `workspace` data while deliberately leaving `dependencies` uninterpreted for C5D.
- Enforce schema-v1 structural rules only: `[compatibility]` owns exactly required non-empty String `language`; `[exports]` is an optional user-keyed table whose values are non-empty Strings and whose empty table is valid; `[workspace]` owns exactly required Array `members`, every member is a non-empty String, duplicates are rejected, and an empty member Array is valid. Exact decoded Strings and source order are preserved; no compatibility grammar, module-name normalization, path interpretation, globbing, environment expansion, or dependency policy is introduced.
- Add Protos-owned conformance for absent/complete/empty optional sections and fail-closed table/field/value/member shapes. `TOOL001-C5C` closes, `TOOL001-C5D` becomes READY for dependency declaration forms plus final schema-v1 closure, and C6 remains dependency-blocked until C5 closes.

## 0.2.189-SNAPSHOT

- Close `TOOL002-D3A2`: bundled `Runner.evaluateSimple(spec, source, executor)` now owns single-case `boolean`, `null`, `integer`, and generic `error` expectation interpretation over the D1 detached observation boundary. Normal expectation mismatches are inert results rather than Test Tool Errors; malformed/unsupported policy still fails closed.
- Preserve exact numeric family semantics for ordinary `integer` expectations by parsing the manifest decimal in Protos and comparing with primitive semantic identity `===`; equal-magnitude Float/fixed-width values therefore do not satisfy an Integer expectation. Each result is a frozen tuple retaining both canonical `passed` and the complete D1 observation for later reporting/aggregation. `TOOL002-D3A3` becomes READY; no Filesystem/TestPlan traversal or `Main.protos` integration is added in A2.

## 0.2.188-SNAPSHOT

- Continue the cost-aware `TOOL001-C5` schema-v1 work with `TOOL001-C5B — manifest base schema`. Add ordinary-Protos `self:ManifestSchemaV1.parseBase(text)` / `baseFromTable(root)` over the closed C1-C4 parser and C5A scale prerequisite. This helper validates only the stable mandatory envelope: the complete schema-v1 root-name allowlist, required Integer `manifest-version` exactly `1`, and required `[package]` table with non-empty String `id`/`version` plus optional non-empty String `locator`.
- Keep the slice boundary explicit rather than publishing a temporary half-validator: the four known optional root sections are permitted by the base root allowlist but deliberately remain structurally unvalidated by `parseBase`; the final full-schema `parse/fromTable` API is not published in C5B. The base result exposes only the ordinary-Protos package model and performs no SemVer, PackageId lexical, locator, compatibility, export, dependency, path, URL or resolution-policy validation.
- Add 12 Protos-owned focused fixtures covering minimal/locator success, future optional-section deferral, unknown root fields, missing/wrong/unsupported manifest generation, required package/table shape, package unknown fields and non-empty package strings. Close C5B and make C5C compatibility/exports/workspace READY; C5D and C6 remain dependency-gated.

## 0.2.187-SNAPSHOT

- Close I022-E2 by integrating cooperative cancellation with D043 `ensure` for the synchronous-cleanup slice. `EnsureExitKind` gains `CANCELLATION`; an observed request with an active ensure frame remains in Task `UNWINDING` instead of publishing a terminal cancelled Future; each crossed ensure records and propagates the exact cancellation transfer after running cleanup in ordinary LIFO order.
- Add explicit later-transfer precedence over delivered cancellation. Cleanup Error or non-local return changes the internal cancellation phase to `SUPERSEDED`, preventing redelivery of the original request and allowing the ordinary Error/return path to determine the Task/Future outcome. Normal cleanup reaches `finishCancellationUnwind()`, which preserves E1's existing child-drain terminalization when applicable.
- Add Protos-source conformance for synchronous cancellation cleanup execution, exact cleanup Error identity, cleanup `^` superseding cancellation, and nested LIFO cleanup; retain narrow Java Task-state focal coverage for the internal UNWINDING/SUPERSEDED transitions. No specification or native-Closure boundary change. I022-E remains IN_PROGRESS and I022-E3 becomes READY for suspension/shielding and cleanup-created structured-child closure.

## 0.2.186-SNAPSHOT

- Start the subdivided `TOOL002-D3` implementation with `TOOL002-D3A1`: add bundled `Runner.readSource(spec, filesystem)` as ordinary Protos policy. It resolves the D2 canonical CaseSpec path through the confined standard Filesystem, consumes the ordered File with bounded 16-read / 64-KiB windows, accumulates exact bytes, closes the File, and decodes UTF-8 exactly once so codec scalars split across read results remain intact.
- Keep D3A1 deliberately below the execution/expectation boundary: no call to `execution(source)`, no PASS/FAIL interpretation, no TestPlan traversal and no `Main.protos` integration. A Protos fixture validates a 1,100,007-byte source crossing the 1-MiB batch boundary and verifies exact UTF-8 bytes around a multi-byte scalar boundary. `TOOL002-D3A2` becomes READY.

## 0.2.185-SNAPSHOT

- Start the cost-aware subdivision of `TOOL001-C5` with `TOOL001-C5A — manifest-scale parser prerequisite`. The first C5 schema corpus exposed that the already-published C3/C4 document implementation consumed host stack linearly: `statements(text)` recursively invoked one Protos Closure per source octet and `table(text)` recursively invoked one Closure per logical statement.
- Preserve the C3/C4 TOML contract while replacing those two unbounded linear-recursion paths with ordered standard collection traversal: `Bytes.each` advances the existing scanner state only at the current consumed index, including multi-octet CRLF/escape/quote consumption, and `Array.each` processes logical statements in encounter order. No TOML syntax, table ownership, canonical node shape or package/schema meaning changes.
- Add one Protos-owned long flat-document regression that parses 96 ordered assignments and observes the first/last values without StackOverflow. Formalize the remaining smaller C5 slices: C5B root/manifest-version/package is READY; C5C compatibility/exports/workspace and C5D dependencies/final schema closure remain dependency-ordered. `TOOL001-C6` stays blocked until C5 closes.

## 0.2.184-SNAPSHOT

- Close I022-E1 as the first publishable tranche of the decomposed I022-E cancellation work. Replace the single internal cancellation boolean with one idempotently recorded request plus explicit `NONE`, `REQUESTED`, `UNWINDING` and `TERMINAL` lifecycle phases. Only `REQUESTED` remains a pending portable observation; once observed, the same request is no longer exposed for redelivery.
- Make the already-normative structured-child cancellation drain explicit inside `UNWINDING`: observation requests cancellation of existing children, the parent waits on its existing child-drain dependency, and drain completion terminalizes cancellation without re-entering parent ordinary code. Preserve the current no-cleanup observable path, including pre-start cancellation, suspended-task wakeup, upstream-wait isolation and immediate cancellation when no structured child remains.
- Add Java-only focal coverage because E1 is intentionally internal scheduler/Task machinery rather than a new Protos-visible feature. Formalize I022-E1/E2/E3 in the implementation ledger: I022-E stays IN_PROGRESS, E2 becomes READY, and no `ensure`, Future public protocol, specification or native-Closure boundary change is made.

## 0.2.183-SNAPSHOT

- Close `TOOL001-C4 — canonical TOML table assembly` with `TOOL001-C4B` (legacy manifest Slice 3B2-B2). Extend ordinary-Protos `self:TomlDocument.table(text)` with TOML 1.0 `[[array-of-tables]]`: the first header creates an Array containing one fresh table, repeated headers append fresh table elements in encounter order, and assignments target the selected fresh element.
- Resolve ordinary/nested headers through the most recently defined array element exactly at the TOML data-model boundary, including nested arrays-of-tables. Reject static-Array append attempts, normal-table/array-of-table redefinition in either direction, inline-table traversal, and child-before-parent-element ordering. Array-of-table provenance remains parser-private `IdentityMap` state; the returned model stays ordinary `{ kind, value }` nodes backed by Maps and Arrays and acquires no package/schema meaning.
- Add Protos-owned conformance for ordered append, nested latest-element routing, implicit-super-table completion and the invalid collision/ordering cases from the TOML 1.0 array-of-tables contract. `TOOL001-C4` and C4B close; `TOOL001-C5` becomes READY for strict manifest schema-v1 validation/model construction while C6/C7 remain dependency-gated.

## 0.2.182-SNAPSHOT

- Close `TOOL002-D2`: grant the bundled Test Tool one explicit standard `Filesystem` capability rooted at `protos/tests/conformance` and backed by a new general read-only tree-confined NIO backend. Nested opens remain relative to pinned `SecureDirectoryStream` handles with `NOFOLLOW_LINKS`; write/create/mutation authority is absent and unsupported host providers fail closed.
- Add bundled `protos/tools/test/Manifest.protos`. The existing TSV manifest is read with bounded windows of ordinary ordered `TextReader.readLine` Futures, aggregated by `Future.all`, parsed in Protos, and materialized with balanced ordinary-Protos chunk accumulation as a frozen inert plan of frozen CaseSpec tuples. Named bundled-Protos accessors preserve the conceptual CaseSpec/TestPlan fields independently of that private representation. The initial stable `caseId` is the validated canonical relative manifest path and is independent of worker/completion order. No case is executed and no expectation is interpreted in D2.
- `protos test` now constructs that plan during normal bundled-tool startup while retaining its existing bootstrap output. A Protos fixture validates the first CaseSpec and proves nested corpus source authority through the standard Filesystem surface. TOOL002-D3 becomes READY; `future-*` remains owned by TOOL002-F.

## 0.2.181-SNAPSHOT

- Start `TOOL001-C4 — canonical TOML table assembly` with the smaller publishable `TOOL001-C4A` tranche. Extend ordinary-Protos `self:TomlDocument` with `table(text)`, producing the existing `{ kind, value }` TOML node model over C3 statement scanning and C1/C2 key/value parsing. Ordinary headers, dotted keys and inline tables normalize into the same nested Map representation without acquiring manifest-schema or package-policy meaning.
- Implement TOML 1.0 ordinary-table ownership rules with internal `IdentityMap` metadata only: implicit super-tables may later receive their own header; dotted-key-created tables cannot be redefined by that header; header-defined tables cannot be redefined through dotted-key traversal; headers may add new sub-tables below dotted-key-created tables; inline tables are closed against later extension; duplicate keys, scalar/table conflicts and repeated headers fail where the invariant is violated. Unsupported scalar nodes remain parser data for later schema validation.
- Apply the repository cost-aware decomposition rule to keep array-of-tables ownership separate: `TOOL001-C4` remains IN_PROGRESS, `TOOL001-C4A` closes ordinary table/dotted/header assembly, and `TOOL001-C4B` becomes READY for `[[array-of-tables]]` plus final TOML table-model conformance. C5 schema-v1 validation, C6 confined `protos.toml` I/O/diagnostics and C7 closure remain dependency-blocked.

## 0.2.180-SNAPSHOT

- Continue `PERF003-A — Collection algorithm Truffle compilability` after the exact external diagnostic against Protos `5404667964dec84b8b8de2ff8dbe7923a5d1dd2e` again preserved `array-reduce` correctness (`528`, `rc=0`) and reduced the remaining failing graph from 150069 to 150036 against the 150000 limit. The bailout count remains 2, so PERF003-A is not closed.
- Refine only ordinary-Protos `Array.reduce` initialization state: after the existing `initialSize > 1` guard, `initialSize` is exactly 0 or 1, so remove the `hasInitial` and mutable `startIndex` bindings, select the same two initialization paths directly, and derive the fold start as `1 - initialSize`. Preserve the pre-callback shallow snapshot, strict left fold, exact reducer calls/effects/failures and zero-or-one initial semantics. No `sort`, Java/runtime, Truffle-boundary, benchmark-specific, or normative change is made. Exact external validation against this publication remains required.

## 0.2.179-SNAPSHOT

- Close I022-D by first making the cooperative Task producer Closure activation itself replay-stable across segments: `invokeInTask()` now reuses one root activation so lexical bindings and ReturnHome identity survive suspension. Then make D043 `ensure(cleanup)` replay-stable across real `Future.value()` suspension in both protected body and cleanup. ENSURE frames now persist BODY/CLEANUP phase, the exact pending normal result/Error/non-local-return transfer and the body replay cursor. If cleanup suspended after the body had semantically exited, replay skips that body instead of reconstructing its transfer or effects.
- Extend the existing per-Task evaluator replay tape with one internal control-primitive operation that consumes the already-exited body's direct Closure-invocation ordinal while advancing to its recorded event boundary. Cleanup therefore reuses the same invocation activation and resumes its incomplete expression range; there is no second continuation stack, programmer-visible cleanup object or native-boundary expansion.
- Add Protos-source Future regressions for root lexical-binding and ReturnHome preservation across suspension, plus conformance for body and cleanup suspension, exact-once effects, exact normal-result and Error identity, selected-handler inactivity across suspended cleanup, pending non-local return, nested LIFO cleanup and two suspension phases in one ensure. I022 remains IN_PROGRESS and I022-E becomes READY for cooperative cancellation unwind and same-request shielding.

## 0.2.178-SNAPSHOT

- Continue `PERF003-A — Collection algorithm Truffle compilability` after the exact external rerun against Protos `eb8b9c588bb363309da5193bf8d98d64d5456cee` again preserved `array-reduce` result `528` and reduced the failing graph from 150500 to 150069 / 150000, leaving only 69 graph-size units while still recording 2 `GraphTooBig` failures. Refine only ordinary-Protos `Array.reduce`: cache the immutable invocation-local `initial` cardinality and fresh internal snapshot cardinality once, reuse them through validation/fold setup, and remove the recursive helper's redundant explicit `null` result expression.
- Keep the balanced left-before-right traversal, exact left-fold accumulator flow, reducer effects/failures, zero-or-one initial semantics and existing 32-element repeated-reduction conformance unchanged. No `sort`, Java/runtime, Truffle-boundary, benchmark-specific, or normative change is made. PERF003-A remains `IN_PROGRESS` pending an exact external rerun against this publication; PERF003-B remains `BLOCKED_BY_DEPENDENCIES`.

## 0.2.177-SNAPSHOT

- Close `TOOL001-C3 — TOML document scanner` (legacy manifest Slice 3B2-A) with ordinary-Protos `self:TomlDocument.statements(text)`. The scanner segments a TOML document into logical source statements while respecting basic/literal strings, multiline strings, comments, CRLF, nested arrays and inline tables; it rejects unterminated strings and unbalanced delimiters without assigning table or manifest-schema meaning.
- Keep the boundary intentionally below document assembly: C3 returns source-compatible statement Strings and does not interpret `key = value`, table headers, dotted-key ownership, duplicate/redefinition policy, arrays of tables, manifest generation, package fields, Filesystem reads or diagnostics. Add Protos-owned fixtures to the existing package-tool TOML conformance corpus; TOOL001-C remains IN_PROGRESS and C4 becomes READY for canonical TOML table assembly.

## 0.2.176-SNAPSHOT

- Close I022-C by publishing D043's standard Closure `ensure(cleanup)` as one audited host-irreducible `Object` native control primitive. The operation validates the semantic Closure receiver, exact arity and semantic Closure cleanup before entering the protected dynamic extent; preserves the body's exact normal result; ignores the cleanup's normal result; runs synchronous cleanup for normal completion, non-local return and Error unwind; preserves nested LIFO ordering; and lets a later cleanup Error/non-local return supersede the pending transfer.
- Integrate I022-B handler selection ordering: an Error handler selected by the body is already inactive while crossed `ensure` cleanup runs, so a cleanup Error cannot recursively re-select the consumed handler. Add Protos-source conformance for exact result identity, normal cleanup, LIFO nesting, Error preservation/precedence, selected-handler deactivation, non-local-return cleanup/precedence, eager validation, Closure-only cleanup and arity/receiver rejection.
- Reconcile the I018 native boundary by increasing `ProtosStandardObjectProtocol` from two to three construction sites and the repository total by exactly one, with provider count unchanged. I022 remains IN_PROGRESS; I022-D becomes READY for suspension/replay closure and I022-E remains the owner of cancellation unwind/shielding.

## 0.2.175-SNAPSHOT

- Continue `PERF003-A — Collection algorithm Truffle compilability` after valid external evidence against Protos `4b2d1c661ed943e51253ec44a324b45e798e1666` preserved `array-reduce` correctness (`528`) and reduced `GraphTooBig` from 40 failures to 2, but left one compiled graph at size 150500 against the 150000 limit. Refine only ordinary-Protos `Array.reduce` traversal so the balanced left-before-right range helper updates its invocation-local captured accumulator instead of threading accumulator/result values through recursive calls.
- Add 32-element repeated-reduction Protos conformance for exact results, 64 reducer calls across two reductions, and invocation-local accumulator isolation. No normative, Java/runtime, Truffle-boundary, benchmark-specific, or `sort` change is made. PERF003-A remains `IN_PROGRESS` pending an exact external rerun against this publication; PERF003-B remains `BLOCKED_BY_DEPENDENCIES`.

## 0.2.174-SNAPSHOT

- Start `TOOL002-D` with `TOOL002-D1`: expose the closed B/C exact-source fresh-Process/captured-execution mechanism to the bundled Test Tool through one bootstrap-local `execution` capability. The capability is not Core/prelude state and owns no manifest, expectation, CaseId, scheduler, reporter or retry policy.
- Add `ProtosDetachedExecutionValue`, a deliberately stricter-than-Actor transfer boundary for execution observations. Scalar and explicitly audited authority-free Object/Array/Bytes graphs are detached into fresh destination identities; Process, Actor/Group refs, streams, Filesystem/File, Closure, Future, task, activation and other execution/resource values fail closed with `NonTransferableValue` rather than leaking a child-Process graph into the tool Process.
- Record the cost-aware TOOL002-D decomposition: D2 will grant/read the confined conformance corpus and construct inert TestPlan/CaseId data in Protos; D3 migrates ordinary scalar/Error expectations; D4 handles the remaining non-Future closure/identity-sensitive expectations. Existing `future-*` expectation policy remains intentionally assigned to TOOL002-F by the already-selected sequence.

## 0.2.173-SNAPSHOT

- Close I022-B by publishing the already-normative `Error.handle(body, handler)` protocol over I022-A's dynamic-control substrate: eager Closure-only validation, ordinary-delegation matching, dynamically innermost selection, selected-frame deactivation before boundary unwind, exact Error identity and non-resumable handler transfer. Explicit `Error.signal()` preselects immediately; runtime-created Error transfers select at Closure invocation boundaries so both paths share one mechanism.
- Preserve pay-for-use/task isolation: Task-backed activations use Task-owned I022 state; direct synchronous activations propagate an existing flow-local state and allocate none until `handle` is actually used. Add Protos-source conformance for matching, nesting, validation, non-resumability, copied receiver rejection and runtime `SlotNotFound` handling, plus narrow Java machinery/native-boundary focal coverage.
- Reconcile the I018 native boundary for the one justified primitive: `ProtosStandardErrorProtocol` moves from one to two native Closure construction sites with provider count unchanged. I022 remains IN_PROGRESS and I022-C becomes READY.

## 0.2.172-SNAPSHOT

- Close I022-A with the internal replay-stable dynamic-control substrate required by D043/I022: add lazy per-task `ProtosDynamicControlState`, semantic Handler/Ensure frame identity keyed by stable invocation identity, pre-unwind frame deactivation distinct from LIFO extent removal, and a replaceable active transfer record for later return/Error/cancellation unwind integration. Structured child tasks receive independent state while synchronous activations continue sharing their existing task association.
- Add Java runtime focal coverage for the machinery-only invariants. Publish no Protos-visible `Error.handle` or `ensure` behavior yet and add no native Closure construction site; I022 remains IN_PROGRESS and I022-B becomes READY.

## 0.2.171-SNAPSHOT

- Close `TOOL002-C — single-case sequential captured execution`: add the test-neutral `ProtosCapturedProcessExecution` wrapper over TOOL002-B so one exact already-compiled Protos entry runs in a fresh semantic Process with private stdin, stdout, and stderr bindings and returns the inert semantic outcome plus detached captured output bytes. The initial capture is deliberately in-memory and sequential; it adds no parallel scheduler, manifest, expectation, retry, worker, remote, cache, reporter, or result-transfer policy.
- Add executable evidence using a real `.protos` tooling fixture that writes independently to stdout/stderr and returns an Integer, verifies per-execution capture separation, and verifies that committed output preceding a semantic Error is retained. The host mechanism remains general and TOOL002-D becomes READY for Protos-owned manifest/expectation migration; cross-Process result exposure to the bundled tool is intentionally not invented in C.

## 0.2.170-SNAPSHOT

- Continue `PERF003-A — Collection algorithm Truffle compilability` with the Protos-side implementation phase. Rewrite only `std:collections/Array.reduce` traversal and the internal merge traversal of stable `sort` from native `Array.each` callback loops into balanced ordinary-Protos range recursion. Preserve the pre-callback shallow snapshot, exact left-fold order and reducer invocation count, stable merge tree/order, two-direction comparator invocation/validation, failure behavior and fresh result semantics while avoiding a generic per-element Truffle boundary or benchmark-specific fast path.
- Add Protos-source conformance over an eight-element reduce/sort case and retain the complete existing LIB001 reduce/sort semantic corpus. PERF003-A remains `IN_PROGRESS` until the exact published implementation revision is validated under the external GraalVM Community JDK 22 / Truffle 24.0.0 / `-Xss128m` diagnostic environment and the two recorded `GraphTooBig` findings are shown absent.

## 0.2.169-SNAPSHOT

- Close `TOOL002-B — general fresh-Process execution mechanism`: add the test-neutral `ProtosFreshProcessExecutor` over the existing host-neutral standalone Process bootstrap, execute the exact precompiled entry as a real RootActor-local cooperative task, and return inert `ProtosExecutionOutcome` data for completed, failed, or cancelled terminal states. Every invocation creates and terminates a fresh semantic Protos Process; semantic Protos Errors are result data rather than CLI/test-specific host exceptions.
- Extract RootActor terminal dispatch from CLI-owned machinery into `ProtosRootTaskExecution` and keep the CLI's existing diagnostics as a consumer translation of the shared outcome. Add Java mechanism tests for fresh Process identity, Future suspension/resume, semantic failure capture, and Process termination. No test manifest, TestPlan, CaseId, scheduler, resource policy, timeout, worker, remote execution, retry, cache, reporter, Core semantic or specification change is introduced.

## 0.2.168-SNAPSHOT

- Close `PERF001-E` by reconciling companion reference evidence `guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565` produced by harness `280173d743b2ed838a89be0ad930b20828d89558` against exact Protos corpus revision `86b35d8bb2d7ab2ad54bc2947e1bf7fbff1fca15`. All 18 cross-language correctness cases pass and startup/warmup/steady-state evidence is retained in 54 raw result records. Separate Truffle diagnostics retain two optimization findings: `collections/array-reduce` and `collections/array-sort` each execute correctly (`rc=0`) but record 40 `GraphTooBig` optimization failures. Introduce `PERF003 — Collection algorithm Truffle compilability` as READY so those findings are handled separately without rewriting PERF001-E baseline evidence. This reconciliation is documentation/project-state only and changes no implementation, specification, distributable artifact or implementation version.
- Select the non-normative TOOL002 scale/distribution architecture in `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`: preserve one fresh semantic Protos Process/RootActor per ordinary attempt while separating physical placement into replaceable local, future amortized OS-worker and future remote backends. Introduce conceptual Run/Case/Variant/Attempt identity layers, separate semantic from infrastructure outcomes, reject exactly-once assumptions for remote execution, and require inert serializable/streamable planning boundaries.
- Refine future massive scheduling around capacity plus resource access/locality, explicit capability provisioning, bounded/artifact-friendly output, deterministic sharding plus optional dynamic assignment, and affected/cache layers only when dependencies/hermetic execution fingerprints justify them. TOOL002-B remains READY and local/test-neutral; this checkpoint changes no Core semantics, runtime, distributable source or implementation version and requires no Maven tests.

- Close D043 / specification revision `0.1.380` by standardizing `body.ensure(cleanup)` as an ordinary Closure-specific `Object` behavior. The exact protocol validates a Closure receiver plus one Closure cleanup before entering the protected extent, preserves the body's exact normal result, runs cleanup exactly once across normal/non-local-return/Error/cancellation exits, treats suspension/replay as remaining inside the scope, and makes a later cleanup control transfer supersede the pending transfer without adding `try`/`finally` syntax, a `Closure` prototype, a general cancellation mask, or resource-specific semantics.
- Formalize `I022 — Dynamic Error handlers and unwind-safe cleanup` as READY with replay-stable dynamic-control, `Error.handle`, `ensure`, suspension, cancellation-unwind and integration slices. Mark LIB004 `BLOCKED_BY_DEPENDENCIES` until I022 is CLOSED rather than bypassing the general Core substrate with File/Filesystem-specific native helpers. This change is specification/project-governance only and does not change the Maven implementation version or executable behavior.
- Complete the required post-TOOL002-A expanded Test Tool comparative architecture checkpoint across pytest/xdist, cargo/libtest/nextest, Go test, JUnit/Surefire/Gradle, Node/Vitest, .NET, ExUnit/Common Test, CTest/GoogleTest, Bazel/Buck2 and RSpec. Retain one fresh semantic Protos Process/RootActor per ordinary case, private streams, explicit capabilities and bounded parallelism while introducing an inert stable-CaseId/TestPlan planning boundary above the general executor and generalizing future shared-resource scheduling from Boolean groups to capacity accounting.
- Confirm that TOOL002-B remains a test-neutral exact-entry fresh-Process mechanism returning inert execution outcomes: hard arbitrary timeout still requires a later physical OS-worker/controller layer, retries must preserve flaky-attempt evidence, and result caching is deferred until a hermetic input/environment/capability model exists. Close the comparative dependency checkpoint and make TOOL002-B READY. This is documentation/governance-only; no normative semantics, runtime, distributable source or implementation-version change is introduced.
- Close `TOOL002-A — Test Tool bundled bootstrap`: add public `protos test` dispatch to the exact toolchain-bundled `protos/tools/test/Main.protos` entry and execute it as ordinary Protos through the existing bundled-tool resolver. Factor package/test entry execution through one small common CLI bootstrap helper while retaining the Package Tool's separately provisioned confined Filesystem capability. The Test Tool receives only its ordinary Process/bootstrap streams in this slice and gains no Filesystem authority, test discovery, assertions, manifests, Process-per-test runner, timeout, parallelism, filtering, reporting, or corpus-migration policy.
- Add Java CLI/bootstrap conformance because the changed subject is Java host/driver functionality, while keeping language-test policy out of Java. Persist the required expanded cross-language/test-system comparative architecture audit as a dependency before `TOOL002-B` fresh-Process execution work begins. No normative Protos specification change is introduced.

## 0.2.167-SNAPSHOT

- Formalize official bundled-tool implementation tracking with the new non-normative `TOOLxxx` family. `TOOL001` retrospectively indexes the already-published Package Tool bootstrap, confined Filesystem 2A/2B, B006 metadata publication, and manifest Slice 3 sub-slices without renaming historical evidence; `TOOL002` promotes the selected Test Tool architecture with TOOL002-A READY and later slices dependency-gated. Keep `CLIxxx` for independently meaningful driver/terminal/dispatch mechanics, `PERFxxx` for project performance engineering, and `LIBxxx` for Standard Library API. No runtime, language, specification, distributable-code, or implementation-version change is introduced.
- Start `PERF001-E` with a fresh closed-surface audit and canonical Protos corpus extension: add six deterministic sequential collection workloads for `std:collections/Array` map/filter/reduce/stable-sort, Core Map lookup/update, and Map-backed Set algebra. Each workload exposes a scalar final-expression correctness value and fixed explicit work shape for later algorithm-equivalent Python/JavaScript translation. PERF001-E becomes IN_PROGRESS pending companion correctness/timing evidence against the exact corpus commit; no language/runtime/library/specification semantics or implementation version change is introduced.
- Close `PERF001-D` by reconciling published reference timing evidence `guillermomolina/protos-benchmarks@52b083cef5f8726f73be869c56f3cd2933e919ab`, produced by exact harness `0a406373c497df1173ff26a3ed4fcada015e0879`. The companion run compares adjacent pre/post-PERF002 revisions `8f363d0146164f99e72210eb44667f4efb7b88e7` and `3c93912a5579326374782a43527fbb51046f8f91` on the same GraalVM Community JDK 22 / external Truffle 24.0.0 environment with `-Xss128m` and a pinned CPU; it retains startup, ordered warmup and steady-state samples for all 11 canonical workloads in interpreter and Truffle modes, plus separate non-timing compilation diagnostics. Post-PERF002 diagnostics have zero optimization failures and zero recorded known bailout/runtime failures across all 11 workloads. PERF001 remains IN_PROGRESS with PERF001-E still READY; this reconciliation changes no implementation, specification, distributable artifact or implementation version.
- Complete `LIB003-D — streaming and text/byte adapters` with `LIB003-D3`: add ordinary `JSON.readEvents(textReader, consumer)` and `JSON.writeEvents(textWriter)` composition over the published D1/D2 event surfaces. Input consumes one `TextReader.readText()` chunk per explicit Future-shaped `read()` call; output validates one D2 event then contributes its deterministic String chunk through ordered `TextWriter.writeText`.
- Preserve explicit I/O boundaries: callers construct borrowing or owning TextReader/TextWriter with their chosen Encoding before adaptation; JSON does not infer ownership, acquire byte authority, close/flush resources, hide suspension, or create another codec/resource family. Each adapter permits one outstanding operation, providing bounded backpressure and becoming terminal after failed/cancelled/uncertain I/O instead of running JSON state past unknown source/target progress.
- Make writer `finish()` Future-shaped with the standard ordered zero-output `TextWriter.writeText("")` barrier, which contributes no bytes or encoder-state transition while preserving predecessor failure/order semantics. Add Protos-source Future conformance for chunked/empty/EOF reader flow, truncation/reuse/overlap failure, deterministic writer output/barrier behavior, downstream Future failure, and post-finish/overlap rejection; tests return Futures to the conformance runner instead of calling pending `Future.value()` outside an Actor-local task.
- Close LIB003-D and make LIB003-E READY. No normative specification or production Java boundary changes are introduced.
- Close `LIB003-E` and the bounded initial `LIB003 — JSON` scope after published `LIB003-E1` executable evidence at `e215952e5459782e95f0d9c73c7bffc948bac943`: Protos conformance covers deep valid/truncated parsing, explicit event-stack streaming, large Array materialization, large exact-decimal round-trip, long incremental String tokenization, and the B -> C -> D1 -> D2 -> B cross-slice pipeline; the existing Java-specific JSON parser stress and Actor graph-transfer boundary regressions plus the full Maven suite also pass.
- Complete `LIB003-E2` as documentation/governance-only architecture reconciliation: the distributable JSON module remains the sole exact-case `std:json/JSON` source and introduces no lowercase alias, generic Serializer/Deserializer hierarchy, reflection/object-binding hook, YAML/XML vocabulary, runtime type classifier, identity-reference persistence convention, hidden Encoding/ownership policy, production Java boundary, or normative specification change. E2 does not increment the Maven implementation version.

## 0.2.166-SNAPSHOT

- Implement `LIB002-A` and close the initial `LIB002 — Text / encoding conveniences` surface with ordinary `std:text/UTF8`, `std:text/UTF16LE`, `std:text/UTF16BE`, and `std:text/Latin1` modules. Each module exposes only `encode`, `decode`, `reader`, `owningReader`, `writer`, and `owningWriter`, delegating directly to the corresponding finalized Core Encoding/TextReader/TextWriter operations.
- Preserve Core argument/result, strict decoding/encoding, fresh/open Bytes, wrapper ownership, I/O authority, ordering, lifecycle, cancellation, and per-flow codec-state semantics without a new runtime value family, registry, default Encoding, automatic detection, production Java/native boundary, or shared mutable state.
- Add real-`std:` Protos conformance for all four one-shot codecs, import caching, borrowing/owning wrapper construction, invalid argument/capability paths, strict malformed/unrepresentable failures, and the explicit rule that helper module instances are not Encoding semantic values. Deferred read-all, Unicode transformation/normalization/locale, registry/discovery, and Core String/Bytes augmentation remain outside this closed initial scope.

## 0.2.165-SNAPSHOT

- Continue `LIB003 — JSON` with `LIB003-D2`: publish fresh ordinary `JSON.eventWriter(consumer)` instances with synchronous `feed(event)` and `finish()` over the JSON-specific D1 event vocabulary. The writer validates structural order, matching container boundaries, one root value, object name/value pairing and duplicate member names while inserting deterministic JSON punctuation itself.
- Emit exactly one semantic String chunk for each accepted event. Reuse the published JSON constructors/encoder for String escaping, strict Boolean/String/Number domains and exact unbounded coefficient/exponent decimal output, preventing a second divergent scalar-encoding contract.
- Keep consumer callbacks synchronous/non-reentrant and terminal on consumer failure; already-consumed output is not rolled back by later invalid events. Keep TextWriter/Future/ownership and byte-I/O lifecycle behavior outside D2 for LIB003-D3.
- Close LIB003-D2 and make LIB003-D3 READY. LIB003-D and top-level LIB003 remain IN_PROGRESS; no normative specification or production Java boundary changes are introduced.

## 0.2.164-SNAPSHOT

- Continue `LIB003 — JSON` with `LIB003-D1`: publish fresh ordinary `JSON.eventParser(consumer)` instances with synchronous `feed(String)` and `finish()` operations and a JSON-specific event vocabulary for object/array boundaries, member names, and null/boolean/string/exact-decimal scalar values.
- Preserve the strict LIB003-B grammar across arbitrary semantic-String chunk boundaries, including split literals/numbers/escapes/surrogate pairs, decoded duplicate-name rejection, exact unbounded coefficient/exponent decimals, one top-level value and trailing-data rejection. Container parsing uses an explicit linked frame stack and does not materialize a JSON tree.
- Make consumer callbacks non-reentrant for the parser and terminal on consumer failure; already-emitted events are not rolled back if later JSON input is malformed. Keep YAML/XML vocabularies, generic Serializer abstractions, object persistence, TextReader/TextWriter lifecycle and byte-adapter policy outside D1.
- Decompose the remaining streaming work into LIB003-D2 incremental event writing and LIB003-D3 I/O adapters. LIB003-D remains IN_PROGRESS and top-level LIB003 remains IN_PROGRESS; no normative specification or production Java boundary changes are introduced.

## 0.2.163-SNAPSHOT

- Continue `LIB003 — JSON` with `LIB003-C`: publish ordinary-Protos `JSON.encode(node)` for the explicit JSON tree model. The encoder validates each visited representation, preserves Array order and retained Object Map traversal order, and rejects malformed or cyclic trees instead of reflecting over arbitrary application objects or inventing references.
- Emit semantic Strings as strict JSON text over UTF-8, escaping quote/backslash and U+0000..U+001F without Unicode normalization. Emit exact Number coefficient/exponent data entirely through unbounded Integer arithmetic (`coefficient` plus optional `e` exponent), with no Float conversion, binary64 formatting, host/JVM decimal formatter, or eager normalization.
- Detect cycles only along the active traversal path with an ordinary `IdentityMap`, so shared acyclic JSON nodes remain valid and are encoded at each occurrence. Add Protos-source conformance for exact scalar/decimal output, escapes/Unicode round-trip, deterministic nested order, shared subtrees, malformed representations, and cycle rejection.
- Close LIB003-C with executable-impact focal/full-suite validation and make LIB003-D READY. Top-level LIB003 remains IN_PROGRESS pending streaming/adapters and final cross-slice conformance; no normative specification or production Java boundary changes are introduced.

## 0.2.162-SNAPSHOT

- Publish `PERF002-A`, the Protos-side implementation/conformance slice of the Truffle compilability and dispatch optimization discovered while preparing PERF001-D. Fixed Sequence and argument-vector child arrays are exposed as compilation-constant exploded loops; ordinary execution avoids the evaluator continuation bridge unless an active cooperative-task segment requires it; immediate selected-method invocation preserves the original receiver and physical `methodHome` without materializing an unobservable extracted Closure.
- Preserve actual extracted-method semantics exactly: a Closure-valued member read still creates a fresh receiver-bound Closure with copied local Closure slots, while only extraction materialization is kept outside Truffle partial evaluation. Deliberately do not add a Closure-type direct-call bypass, so ordinary local `call` shadowing remains observable.
- Add Protos-source conformance for Closure-local `call` shadowing and extracted-method freshness/receiver/local-state preservation. `PERF002-A` is repository-native and requires only Protos static/focused/full/package/license validation; `PERF002-B` remains READY for GraalVM/Truffle/container validation in `guillermomolina/protos-benchmarks`. PERF002 remains IN_PROGRESS until that external evidence is published and reconciled.

- Close `PERF002-B` and `PERF002` by reconciling published non-timing external evidence `guillermomolina/protos-benchmarks@c69248714a60dd62894164bf5332b55f780d6fe4` produced by harness `224ce852f550a7d9126fad9f5923a9a2fd8194cc` against exact Protos revision `3c93912a5579326374782a43527fbb51046f8f91`. GraalVM Community JDK 22 with external `truffle-runtime:24.0.0` and `-Xss128m` passes the semantic smoke, canonical 11x2 interpreter/Truffle correctness matrix, known Truffle bailout/runtime guard, and 10/10 consecutive polymorphic-dispatch Truffle stability gate with 238 successful optimization events and zero optimization failures. This is project-status reconciliation only: no implementation, specification, distributable artifact, timing result, or implementation-version change is introduced.

## 0.2.161-SNAPSHOT

- Close package-tool Filesystem Slice 2B / B006: provision the bundled package tool with explicit confined project-metadata read authority, write-only `createNew` staging authority, and namespace-mutation authority. Staging content is written through standard `File.write`/`close`, publication uses standard `Filesystem.replace`, and explicit abandoned-stage cleanup uses standard `Filesystem.remove`; ordinary application CLI sessions still receive no Filesystem authority.
- Add `self:MetadataPublication` in ordinary Protos code. It validates Path/String/Encoding inputs before staging creation, refuses to overwrite an existing staging name through `createNew`, completes the staging write and close before atomic replacement, and exposes explicit discard rather than silently deleting a colliding stage. Protos-owned fixtures exercise both `protos.toml` and `protos.lock`, collision preservation, pre-stage validation, forbidden targets, and cleanup.
- Preserve the audited Core native boundary at 107 construction sites across 30 providers: no package-specific native rename/write primitive, ambient filesystem access, new Core native Closure site, or normative specification change is introduced. I021 remains CLOSED and B006 transitions READY -> CLOSED.

## 0.2.160-SNAPSHOT

- Close I021-B with a production confined direct-child NIO Filesystem backend for D042 namespace-entry replacement/removal. The backend retains a `SecureDirectoryStream` for the authority root, applies `replace` through one atomic relative `move`, applies non-recursive `remove` through relative `deleteFile`, never pre-classifies or follows the final entry, and fails closed when the host/provider cannot provide the required transition.
- Keep read authority and namespace-mutation authority independent: `ProtosNioConfinedFilesystemBackend` delegates existing-file read opens to the established read-only backend while requiring a separate exact direct-child allowlist for `replace`/`remove`. The current package-tool CLI continues using `ProtosNioReadOnlyFilesystemBackend`, so I021-B does not silently make package metadata writable or grant staging policy ahead of the later package-tool integration.
- Add host-boundary conformance for absent/existing-target replacement, hard-link same-resource no-op, source/target symlink non-follow behavior, non-recursive removal, authority rejection, and fail-closed providers. No Core native-Closure construction site or normative specification changes are introduced; I021-C becomes READY and B006 remains READY.

- Close I021 with I021-C without changing the implementation version: execute ordinary Protos-source `Filesystem.replace`/`remove` programs through the production `ProtosNioConfinedFilesystemBackend`, covering exact Filesystem success identity, fresh Future results, authority failures as `IOError`, hard-link same-resource no-op, final-symlink non-follow replacement, and non-recursive removal failure.
- Reconcile the final I021 architecture/status boundary: the Core native-Closure inventory remains 107 construction sites across 30 providers, the bundled package tool remains deliberately read-only, and B006 stays READY for the separate write/staging-authority and metadata-publication integration.

## 0.2.159-SNAPSHOT

- Start I021 with I021-A: add the host-neutral asynchronous Filesystem namespace-mutation substrate for D041 `replace`/`remove`, including fresh Future results, eager Path-domain validation, independent operation state, pre-commit cancellation, exact Filesystem success results, and one per-operation effect/commit cutover that prevents cancellation from splitting a successful atomic backend effect from its Protos commitment.
- Widen the host-provisioned Filesystem bridge to the standard `open`, `replace`, and `remove` selectors without adding a Java native-Closure construction site: one audited operation-Closure helper serves all three resource/capability selectors. Backends that do not implement namespace mutation fail those valid operations as `IOError` instead of gaining ambient authority or a host fallback.
- Keep B006 READY and package metadata mutation blocked on the production confined namespace backend: I021-A establishes the protocol/substrate only; I021-B remains the next dependency.
- Correct D041 with D042 / specification revision `0.1.379`: `Filesystem.replace`/`remove` now select final namespace entries directly without following them instead of requiring a non-atomic ordinary-file preclassification. Unsupported atomic entry-kind combinations remain `IOError`, removal is non-recursive, I021-A remains valid, and I021-B continues as the production-backend dependency.

## 0.2.158-SNAPSHOT

- Resolve B006 normatively with D041 / specification revision `0.1.378`: add the minimal general confined file-entry `Filesystem.replace(sourcePath, targetPath)` and `Filesystem.remove(path)` surface, with failure-atomic namespace visibility, explicit commitment/cancellation/failure aftermath, stable already-open File binding, and no implicit namespace crash-durability guarantee.
- Transition B006 from BLOCKED to READY and formalize I021 as the implementation owner for the new general Filesystem namespace surface. Package-tool metadata mutation remains disabled until I021 is published; no package-specific native rename/truncate-write workaround is authorized.
- Continue package-tool manifest Slice 3 with Slice 3B1 by completing the TOML 1.0 String surface required by manifest schema v1. `self:TomlSyntax` now handles multiline basic and literal strings, first-newline trimming, deterministic CRLF-to-LF normalization, basic-string line continuations, one/two quote runs inside multiline strings, and the one/two content quotes permitted immediately next to a closing triple-quote delimiter. Multiline quote runs are consumed by an exclusive branch so they cannot fall through and be appended twice.
- Add Protos-owned conformance for multiline basic/literal values, line continuation folding, CRLF normalization, internal single/double quote runs, four/five-quote closing boundaries, overlong closing runs, and literal newlines rejected by single-line strings. No production Java parser, Filesystem write, lock mutation, schema/CLI behavior, Standard Library dependency, or LIB work item is introduced; Slice 3 remains open.

## 0.2.157-SNAPSHOT

- Close `CLI007` by executing each non-interactive standalone entry CallTarget as a real cooperative task owned by the Process RootActor and driving that RootActor execution domain until terminal. This realizes the existing pending `Future.value()` suspension contract for file/`-e` programs instead of leaking the host-side missing-task implementation error. Preserve ordinary Protos Error mapping and use the same task entry path for the bundled package tool; the persistent REPL model is intentionally unchanged.
- Restore executable validation for every shipped example/tutorial source that depends on CLI `print(...)`: dynamically discover all matching `.protos` files under `protos/examples` and `protos/tutorials`, execute each unchanged through the real standalone CLI, and require successful completion, observable stdout and blank stderr, including Future/Actor/ActorGroup learning programs. No normative specification change and no native-Closure boundary expansion.


## 0.2.156-SNAPSHOT

- Start package-tool manifest Slice 3 with Slice 3A: add an internal `self:TomlSyntax` key/value engine written entirely in Protos. It scans semantic String input as UTF-8 octets and establishes reusable parsing for bare/quoted/dotted keys, single-line TOML basic/literal strings, booleans, signed and radix integers with the TOML signed-64-bit boundary, nested arrays with comments/trailing commas, and inline tables with dotted keys and duplicate rejection. Bare scalar forms outside the manifest generation 1 value model are retained as explicit `unsupported` nodes for later schema validation instead of acquiring package meaning.
- Keep this sub-slice internal and non-authoritative until Slice 3 closes: TOML multiline string forms, full document/table assembly, schema-v1 validation, project Filesystem reads, and package CLI diagnostics remain for the next sub-slice. Protos fixtures own parser behavior while a small Java test harness only executes them through the existing bundled-tool resolver. No production Java parser, `std:toml` dependency, metadata mutation, lock behavior, normative spec change, or `LIBxxx` item is introduced.

## 0.2.155-SNAPSHOT

- Close package-tool Filesystem Slice 2A by provisioning the bundled Protos package tool with one explicit read-only Filesystem capability rooted at the launcher's project directory and restricted to the exact direct-child names `protos.toml` and `protos.lock`. The NIO backend uses a pinned `SecureDirectoryStream` plus `NOFOLLOW_LINKS` when the host provider can preserve the standard confinement contract and fails closed otherwise; ordinary CLI applications still receive no Filesystem authority.
- Exercise the capability from ordinary Protos source through `Path`, `filesystem.open(...).value()`, `TextReader`, and `Encoding.UTF8`; no package-specific file-read primitive or Standard Library dependency is introduced. Record B006 for metadata mutation because Filesystem v0.1 explicitly leaves rename/replace and richer namespace operations undefined, so Slice 2B must not publish `protos.toml`/`protos.lock` via fragile in-place truncate/write or a `PackageNative` escape hatch.

## 0.2.154-SNAPSHOT

- Close `CLI006` with a standalone CLI-owned `print(value)` binding installed as an ordinary Closure only in normal initial CLI sessions. String values print their contents directly; other values reuse CLI display rendering; each call delegates one complete line through a borrowing standard `TextWriter` over the already-provisioned Process stdout capability and Encoding, then returns canonical `null`.
- Make file and `-e` execution explicit-output-only instead of implicitly echoing the final evaluation result; retain the REPL general result-display policy. Add a Protos-source CLI fixture plus focused host/bootstrap/architecture conformance and end-to-end dogfooding of the published hello-world and values tutorial. Preserve the bundled `protos package` tool bootstrap without injecting `print` into its internal tool context. No normative specification change and no Core native-Closure provider/site expansion.


## 0.2.153-SNAPSHOT

- Establish the first bundled-tool bootstrap slice for the package-system architecture. `protos package` now launches an exact toolchain-bundled Protos entry from `protos/tools/package` without reading a project manifest, lockfile, package store, or ambient module search path. Tool-local `self:` imports resolve to internal `bundled-tool:` ModuleKeys while `std:` remains delegated to the selected Standard Library resolver.
- Keep package policy out of Java: the bundled Protos probe imports a second bundled module, observes the ordinary `process.args()` snapshot, and writes through the ordinary Process stdout/TextWriter path. The host change is limited to exact bundled-tool discovery/loading and CLI dispatch; no package resolution, filesystem authority, registry/networking, new public `tool:` namespace, normative semantics, or Standard Library work item is introduced.

## 0.2.152-SNAPSHOT

- Close `I020-D` and the post-Ixxx `I020` audit reconciliation by implementing D040 missing-`methodHome` super semantics. Core now publishes source-backed `InvalidSuper -> Error`; after the complete ordinary argument/spread vector finishes, a super send without `methodHome` signals one fresh `InvalidSuper` occurrence before any lookup, while valid method-bound super lookup and `SlotNotFound` behavior remain unchanged.
- Add focused Java and implementation-independent `.protos` conformance for `InvalidSuper` parentage/freshness, no host `IllegalStateException` leakage, argument-before-dispatch precedence, left-to-right exactly-once effects, spread precedence/order, no receiver fallback, and the root-`Object` empty-search `SlotNotFound` case. B005 and I020 are closed with no normative specification change and no native-Closure boundary expansion.

## 0.2.151-SNAPSHOT

- Continue `LIB003 — JSON` with `LIB003-B`: publish strict `JSON.parse(text)` in ordinary Protos source over `Encoding.UTF8` octets. The parser accepts every JSON top-level value, rejects comments/trailing commas/extra roots, rejects duplicate member names after escape decoding, handles JSON escapes and surrogate pairs without Unicode normalization, and preserves deterministic Object insertion order.
- Parse JSON Number syntax exactly into LIB003-A's unbounded Integer `coefficient * 10^exponent` representation without Float conversion or host/JVM decimal parsing. Leading zeroes, incomplete decimal/exponent forms, leading `+`, NaN and infinities are rejected.
- Keep JSON structural nesting off the host recursive-descent stack with explicit linked parser frames. Normal-mode dispatch snapshots top-level/container state before consuming each octet, so an opening delimiter is routed exactly once. JSON Arrays use balanced power-of-two chunk accumulation rather than quadratic element-by-element growth.
- Close LIB003-B with executable-impact focal and full-suite validation; keep LIB003-C READY. No normative specification or production Java boundary changes are introduced.

## 0.2.150-SNAPSHOT

- Start `I020 — Post-Ixxx implementation audit reconciliation` with I020-A: execute the already-canonicalized `super.message(arguments...)` operation end-to-end for method-bound activations. Super lookup starts strictly at the parent of the physical `methodHome`, preserves the original dynamic receiver, binds any newly selected Closure to its own physical lookup home through the existing invocation path, and uses the same ordered argument/spread vector machinery as ordinary sends.
- Add focused Java and implementation-independent `.protos` conformance for multilevel delegation with dynamic receiver state, nested Closure capture of `methodHome`, super-send spread arguments, and `SlotNotFound` after the defined super lookup origin. I020-A adds no native Closure construction site and changes no normative specification.
- Record B005 instead of inventing semantics for executing `super.message(...)` when the activation has no `methodHome`: the normative execution spec defines `parent(methodHome)` lookup but does not define that absent-home failure, while the `InvalidSuper` branch exists only in non-normative abstract-runtime pseudocode. I020-B and I020-C remain independently READY; I020-D is BLOCKED on B005.

## 0.2.149-SNAPSHOT

- Start `LIB003 — JSON` with `LIB003-A`: publish exact-case `std:json/JSON` as an ordinary Protos Standard Library module and establish an explicit JSON data model made only from fresh ordinary objects, Arrays, Maps, Strings, canonical Booleans/null and exact unbounded Integers. No JSON runtime family, `typeOf` mechanism, reflection-based object serializer, generic serialization hierarchy or production Java boundary is introduced.
- Add constructors for all six JSON value categories: `nullValue`, `boolean`, `string`, exact decimal `number(coefficient, exponent)`, ordered `array`, and String-keyed `object` over alternating name/node pairs. Number data remains exact as coefficient × 10^exponent without Float conversion or eager decimal normalization; JSON Array payloads use the language's existing fresh frozen trailing-rest capture, while object construction rejects duplicate semantic String names instead of silently choosing first/last-wins behavior.
- Persist `docs/project/LIB003_JSON_DESIGN.md` with the comparative JSON/YAML/XML/object-persistence audit, strict parser/encoder direction, Unicode/duplicate/order/streaming boundaries and planned LIB003-B/C/D/E slices. Reconcile stale LIB003 dependency text now that LIB001 and I015 are closed. LIB003-A is CLOSED; top-level LIB003 remains IN_PROGRESS.

## 0.2.148-SNAPSHOT

- Close `LM005 — Concurrent Language Maturity` with `LM005-C`: add a Group-aware conformance harness over ordinary `.protos` programs and the already-closed I011 ActorGroup surface. The harness drives a real Process RootActor, deterministic test-host Actor bootstrap and scheduler work, while assertions remain host-side. No test-only Protos syntax, production runtime behavior, native boundary, or normative specification change is introduced.
- Add language-level coverage for complete-vector `Actor.group` validation, stable GroupRef aliases versus fresh separate acquisitions, requests issued before members become READY, routing whose assertion permits any eligible member, synchronous argument snapshotting, GroupRef Actor transfer preserving semantic identity, terminated-member exclusion, and the communication-only GroupRef surface. Add an executable ActorGroup example and tutorial `11-actor-groups`; LM005-A/B/C and top-level LM005 are CLOSED.

## 0.2.147-SNAPSHOT

- Close I015 with I015-E final Encoding/Text I/O integration. Replace the provisional one-shot-only host codec boundary with explicit transactional per-flow streaming decoder/encoder factories carried by immutable Encoding descriptors. Mandatory `Encoding.UTF8`/`UTF16LE`/`UTF16BE`/`Latin1` remain the same strict, matching-BOM-consuming public descriptors; trusted hosts may explicitly provision configured portable or additional host Encoding values with strict/replacement and initial-BOM policy without adding a name registry, public constructor, ambient discovery or I/O authority.
- Unify TextReader over the Encoding streaming contract for portable and host descriptors. Decoder previews are reversible checkpoints until the surrounding read commits, so readText/readLine cancellation preserves zero logical consumption while stateful decoder transitions, replacement extents and exact source-octet line budgets remain deterministic. Portable replacement follows Unicode maximal-subpart segmentation across native/source chunk boundaries; strict remains default; explicit initial-BOM preservation emits U+FEFF as ordinary text and therefore participates in line byte accounting.
- Unify TextWriter over transactional per-flow encoder state for portable and host descriptors. Complete payload encoding is validated before target-visible contribution, empty write performs zero encoder transition, committed writes install the next encoder state in operation order, downstream uncertainty retains the permanent output-failure frontier, and close explicitly finalizes encoder state and propagates required final bytes before optional owned-target release. Final cross-slice conformance covers host reader/writer acceptance, independent flow state, close finalization, portable and host replacement, BOM policy, I015-A transfer invariants and I015-B/C/D regressions. No native Closure construction site is added by E; the post-I015 I018 boundary remains 30 providers / 107 sites. I015 and I015-E are CLOSED; LIB002 becomes READY for its own fresh ordinary-library design/audit.

## 0.2.146-SNAPSHOT

- Continue `LM005 — Concurrent Language Maturity` with `LM005-B`: add a deterministic Actor-aware conformance harness that executes ordinary `.protos` programs inside a real Process RootActor while a test-only module resolver and scheduler executor drive spawned Actor bootstrap and turns. Assertions stay host-side; no test-only Protos syntax, privileged language object, production runtime behavior, or normative specification change is introduced.
- Add language-level coverage for stable `Actor.current()` identity, module-based `Actor.spawn`, request/reply and snapshot transfer, child/root ActorRef identity, persistent Actor-local state, same-sender concrete-Actor `send` then `request` FIFO, graceful `stop`/`termination`, and synchronous invalid/non-transferable spawn failures. Add Actor examples and tutorial `10-actors`. LM005-B is CLOSED and LM005-C becomes READY.

## 0.2.145-SNAPSHOT

- Start `LM005 — Concurrent Language Maturity` with `LM005-A`: extend the implementation-independent language-conformance corpus with terminal-Future expectations, while keeping assertions in the host runner rather than adding test-only Protos syntax or privileged testing objects.
- Add Protos conformance for closure `future()`, fresh Future identity, `then` transformation and automatic Future flattening, deterministic `Future.all` input ordering/empty input, pre-start cancellation, and eager rejection of a non-invokable `then` transform. Add an executable Future-chain example and tutorial `09-futures`. No runtime or normative specification behavior changes; LM005 remains IN_PROGRESS and LM005-B becomes READY.

## 0.2.144-SNAPSHOT

- Implement I015-D source-backed standard `TextWriter` with borrowing `TextWriter(target, encoding)` and explicit `TextWriter.owning(target, encoding)` construction. Construction validates ByteWritable authority, exact portable Encoding-family membership and owning Closable authority synchronously before wrapper creation or target I/O; each success creates a fresh wrapper exposing exactly `writeText`, `writeLine`, `flush`, and `close`.
- Add one ordered text-output domain. `writeText(text)` and `writeLine(text)` validate complete portable UTF8/UTF16LE/UTF16BE/Latin1 payload encoding before target contribution; `writeLine` appends canonical LF in the same logical operation. `writeText("")` performs no encoder invocation/state transition/BOM/flush/reset/target write but remains ordered behind earlier operations. Encoding failure is zero-output and non-poisoning; once target write/flush delegation crosses the wrapper commitment frontier, downstream failure permanently fails later output without guessed replay.
- Compose Flushable and Closable/ownership semantics with I014 lifecycle machinery. Wrapper `flush()` propagates through an immediate Flushable target and otherwise ends at the ByteWritable boundary; close cuts over accepted-but-uncommitted operations, waits for already-committed output aftermath, does not imply flush, and closes the target only for `owning`. A prior wrapper-output failure remains the primary close failure while owned-target close is still attempted. I015-D covers portable encoders; host-provided incremental encoder integration remains I015-E. Register the two-site TextWriter resource bridge in I018, growing the definitive boundary by exactly one provider and two construction sites. I015-D is CLOSED and I015-E becomes READY.

## 0.2.143-SNAPSHOT

- Close the focused LIB001-E audit and complete `std:collections/Array` with eager sequential `reduce(array, reducer, ...initial)` and stable `sort(array, less)` as ordinary Protos library behavior; close top-level LIB001 after final cross-slice validation.
- Define `reduce` as a strict left fold over a pre-callback shallow snapshot: zero or one initial value, `null` for empty/no-initial, exact singleton pass-through without reducer invocation, exact initial pass-through for empty/seeded reduction, and `(accumulator, element)` callback order with ordinary failure/control propagation.
- Define `sort` as a fresh open stable Array result using one canonical sequential merge-sort tree; each merge decision calls `less(left,right)` then `less(right,left)`, accepts only canonical Booleans, preserves left order for comparator-equivalent elements, and signals fresh `InvalidComparatorResult` or `InvalidComparatorOrder` occurrences for the existing invalid-result/antisymmetry failures.
- Keep sequential reduce/sort free of P/Future/transfer/cancellation semantics, add no production Java or generic collection hierarchy, and transition LIB003 from its LIB001 dependency block to OPEN pending its own fresh serialization/text dependency audit.

## 0.2.142-SNAPSHOT

- Implement I015-C `TextReader.readLine()` / `readLine(maxBytes)` inside the same ordered decoder/input domain introduced by I015-B. Line framing recognizes LF, CR and CRLF; terminators are consumed and omitted, empty lines are preserved, EOF-final unterminated text is returned once, and EOF with no remaining text returns null. A terminating CR completes immediately without waiting for more backend input; a possible later LF is folded as the already-completed CRLF terminator before the next logical text operation can observe it.
- Enforce exact encoded-source-octet line budgeting for `readLine(maxBytes)`: positive Integer-family validation happens as a failed Future before I/O; initial matching BOM bytes and the encoded line terminator are excluded; valid pre-terminator scalar extents count exactly; LineTooLong is established as soon as valid content exceeds the bound and permanently fails text reading without scanning/discarding to a later terminator. Strict malformed-input vs size-failure precedence follows source order.
- Preserve I015-B cancellation/error/ownership semantics across mixed `readText` and line requests: one queue determines invocation order, successful cancellation consumes no logical text, partial lines are never returned on pre-terminator I/O/decoding failure, completed CR lines are not retroactively failed by later input, and permanent line/decoding/I/O failure prevents later source consumption. I015-C adds no native Closure construction site; the TextReader provider remains a two-site resource bridge and the definitive I018 provider/site totals are unchanged. I015-C is CLOSED and I015-D becomes READY.

## 0.2.141-SNAPSHOT

- Close the fresh LIB001-D Array API audit and publish `std:collections/Array` with eager sequential `map(array, transform)`, `filter(array, predicate)`, and `findIndex(array, predicate)` as ordinary Protos library behavior over the existing Core Array surface.
- Capture one shallow ascending-index Array snapshot before any user callback; `map` returns a fresh open same-length Array, `filter` returns a fresh open stable-order selected Array with exact source element identities, and `findIndex` stops at the first matching snapshot index or returns `null`.
- Require canonical Boolean predicate results for `filter`/`findIndex`, signal fresh `InvalidPredicateResult` occurrences otherwise, and explicitly keep empty-input callbacks uninspected because Standard Library source has no hidden non-invoking callability preflight primitive.
- Implement variable-length filtering in Protos source with balanced range composition and ordinary multi-spread Array construction, avoiding a new runtime builder/native boundary and avoiding quadratic one-element-at-a-time result rebuilding; leave `reduce`/`sort` to LIB001-E's required fresh audit.

## 0.2.140-SNAPSHOT

- Implement I015-B standard `TextReader` borrowing/owning construction and progress-oriented `readText()`. `TextReader(source, encoding)` and `TextReader.owning(source, encoding)` are source-backed frozen-prelude factory operations; constructors validate ByteReadable capability, exact Encoding-family membership and owning Closable authority synchronously before wrapper creation or I/O. Each success creates a fresh wrapper with exactly `readText` and `close`.
- Add one ordered TextReader input/decoder domain with transactional strict portable UTF-8/UTF-16LE/UTF-16BE/Latin1 decoding, initial matching-BOM consumption, incomplete-sequence retention, progress as soon as valid text is returnable, valid-prefix delivery before a later malformed error, permanent text-side failure once decoding/underlying I/O failure becomes an operation outcome, and stable no-more-source-consumption behavior after failure.
- Integrate readText cancellation and close with the I014 I/O lifecycle machinery: queued/active cancellation is zero logical consumption even when lower read-ahead completes late, close irreversibly cuts over uncommitted reads, borrowing close leaves the source open, owning close explicitly releases a Closable source, and reader failure does not itself close the source. I015-B intentionally covers portable Encoding streaming; I015-E retains host-provided streaming-Codec integration because the published I015-A host Encoding boundary is one-shot only. Register the two-site TextReader resource/capability bridge in I018, moving the audited boundary from 28 providers / 103 sites to 29 providers / 105 sites. I015-B is CLOSED and I015-C becomes READY.

## 0.2.139-SNAPSHOT

- Close LIB001-C by completing the initial `std:collections/Set` and `std:collections/IdentitySet` surfaces with fresh `union`, `intersection`, and `difference` results plus `sameMembers`, `isSubset`, `isSuperset`, and `isDisjoint`; no runtime Set family, wrapper, tag, generic collection hierarchy, or production Java path is added.
- Preserve deterministic algebra order and representatives through ordinary Map/IdentityMap operations: union inserts left then right into a fresh keyed result, intersection/difference retain matching/nonmatching left representatives in left traversal order, and every result remains a fresh open Map-backed Set with canonical `true` markers.
- Implement predicate short-circuiting with the existing Protos non-local return mechanism, keep ordinary Map `==`/`hash` unchanged in favor of explicit `sameMembers`, and add focal conformance for numeric-equality versus identity membership, traversal order, short-circuit key-search effects, freshness/open state, and Actor transfer of Set data while module behavior remains Actor-local.

## 0.2.138-SNAPSHOT

- Close LIB001-B for `std:collections/Set` and `std:collections/IdentitySet` with ordinary Protos `add`, `remove`, and member-only `each`: `add` performs exactly one underlying `atPut(element, true)`, `remove` exactly one keyed removal, and both return the exact Set object after normal completion.
- Preserve the underlying Map/IdentityMap open/closed/frozen mutation laws, existing representative/insertion position on duplicate add, absent-remove Error behavior, and insertion-order shallow-snapshot traversal; Set `each` adapts the keyed `(element, marker)` callback to exactly `block(element)` without adding a callability primitive or runtime Set family.
- Add focal conformance for both modules covering receiver identity, canonical `true` markers, closed/frozen state, duplicate representatives, snapshot mutation, deterministic visit order, empty/non-empty callback validation, arity/invocation failure propagation, and transition LIB001-C to READY.

## 0.2.137-SNAPSHOT

- Implement I011-21 / D039 public ActorGroup acquisition with the exact frozen-Core selector `Actor.group(firstMember, additionalMembers...) -> GroupRef`: validate the complete evaluated argument vector as ActorRefs before cutover, create one fresh Group identity plus one fresh GroupRef acquisition, and collapse duplicate Actor incarnations into set membership.
- Bind Core-created Group lifetime to the caller Actor's Process without exposing a Group/controller handle: owning-Process termination terminates the Group before member-lifecycle callbacks can continue routing, while Group termination never stops member Actors and existing GroupRefs remain bound to the same terminated Group identity.
- Reuse the I011-15..20 GroupRef/routing/transport machinery and keep service discovery, post-creation membership control, placement, endpoints, and transport selection outside Core v0.1. Close B004 and top-level I011 after focused and full-suite validation; record the reviewed native boundary expansion from 102 to 103 sites across the same 28 providers.

## 0.2.136-SNAPSHOT

- Close I019-A by renaming the Core Actor source from `actor.protos` to `Actor.protos`: `Actor` is the dominant public owner of the source while `_coreActorRefPrototype`, `_coreGroupRefPrototype`, and `_coreSendOperationPrototype` are private subordinate bootstrap identities.
- Refine the Core naming rule from strict one-object ownership to dominant public conceptual ownership with subordinate private helpers; keep `error_taxonomy.protos` and `prelude.protos` descriptive because they are true aggregation/responsibility sources, and keep `import.protos` because it already preserves the exact lowercase public facility name.
- Update Core bootstrap, the executable source-naming guard, current architecture documentation, and the canonical implementation ledger without changing normative semantics or the native boundary.

## 0.2.135-SNAPSHOT

- Implement I019 Core source naming reconciliation without changing normative semantics: rename the 26 distributable one-owner Core sources so each filename preserves the exact canonical Protos object/prototype name and case.
- Rewrite every live physical-source reference under `src/` and `protos/`, including source-loading tests, and require a zero-result post-change scan for all superseded names; historical CHANGELOG evidence remains untouched.
- Keep `actor.protos`, `error_taxonomy.protos`, `prelude.protos`, and `import.protos` as reviewed responsibility/aggregation or exact-lowercase facility sources; normalize exact APL Part 5 notices on every renamed source and add an executable naming guard. I018 remains closed and the audited native boundary is unchanged.

## 0.2.134-SNAPSHOT

- Resolve B004 normatively with D039: Core v0.1 adds exactly one direct ActorGroup acquisition surface, `Actor.group(firstMember, additionalMembers...) -> GroupRef`, using explicitly held ActorRef capabilities and creating no public Group handle, registry, discovery namespace, endpoint, placement, or transport API.
- Define synchronous creation cutover, initial membership, caller-Process ownership/lifetime, fresh GroupRef acquisition identity, zero-eligible-member behavior, and explicit absence of Core post-creation membership/control or Group termination selectors; GroupRef remains communication-only authority.
- Keep service discovery explicitly outside Core v0.1: no name/identity lookup, rebinding contract, TTL/watch/federation, or ambient reacquisition API is introduced. B004 moves `BLOCKED -> READY`; I011 remains IN_PROGRESS until the newly specified acquisition surface is implemented and validated.


## 0.2.133-SNAPSHOT

- Implement I011-20 remote ActorGroup routing by allowing the internal Group membership view to contain either local READY Actors or already-materialized remote ActorRefs carrying the I011-19 host-neutral transport route; no public membership, discovery, endpoint, or transport-selection API is added.
- Extend the internal transport delivery SPI with state observation so Group routing can react without polling: known pre-acceptance transport failure may requeue/select another eligible member, while accepted or acceptance-uncertain work immediately leaves Group routing ownership and is never transparently replayed.
- Preserve the original logical send/request snapshot across remote-member selection and pre-acceptance rerouting; Group SendOperation retry is explicit after remote uncertainty, and Group request acceptance uncertainty fails with RequestOutcomeUncertain. B004 remains limited to the still-undefined public Group/GroupRef acquisition/discovery surface.

## 0.2.132-SNAPSHOT

- Close I017 with the I017-F integrated Process conformance pass. Exercise the published A/B/C/D1/D2/E1/E2/E3 surfaces together: one logical standalone Process/RootActor authority, canonical args/environment reacquisition through independently rematerialized Actor-local Process proxies, fresh ordinary Actor-copy identity for already-acquired snapshots, exact bootstrap-local optional Filesystem separation, independent byte-stream capability shape with stable Encoding associations, and Process/stream/Filesystem exclusion from P where specified.
- Revalidate the Process termination cutover across the public eight-accessor surface and retain the focused D1 stream-lifecycle termination test in the closure suite. No I017 production behavior, selector, capability, host authority, or native Closure construction site is added by F.
- Complete the post-I017 I018 re-audit at exactly 28 Java native-Closure providers / 102 construction sites. Reconcile project state: I017 is CLOSED; I011 no longer lists Process/bootstrap integration as an outstanding dependency; LIB004 Filesystem/process conveniences becomes READY for its own focused design/audit now that I016 and I017 are both closed.

## 0.2.131-SNAPSHOT

- Implement CLI005 portable Standard Library naming: `std:` logical names preserve ASCII letter case as part of canonical `ModuleKey` identity and every physical path component must match the distributed spelling exactly even on case-insensitive filesystems.
- Reject case-fold-equivalent path ambiguity, every case spelling of the reserved `core` first segment, and Windows reserved device-name segments while preserving absolute no-fallback resolution and the hidden `.protos` extension.
- Rename the initial LIB001 modules to canonical `std:collections/Set` and `std:collections/IdentitySet`, backed physically by `Set.protos` and `IdentitySet.protos`, with no aliases for the superseded lowercase spellings; LIB001-B remains READY.

## 0.2.130-SNAPSHOT

- Implement I017-E3 standalone host/CLI Process bootstrap. `protos <file> [args...]` and `protos -e <source> [args...]` now capture only trailing application arguments into the stable Process args snapshot; launcher file/source identity is excluded. The REPL owns one persistent Process with an empty args snapshot for the whole session.
- Capture the host Environment exactly once before the first source expression. Native environment-name representability and identity are probed through an isolated `ProcessBuilder.environment()` map, preserving the JDK native platform rules without hard-coded POSIX/Windows case folding or mutation of the real environment. stdin/stdout/stderr are independently established byte bindings over the CLI-supplied streams and the CLI explicitly selects standard UTF-8 descriptors as its host text associations.
- Add host-neutral `ProtosStandaloneProcessBootstrap`, route non-importable standalone entries through the same E2 RootActor bootstrap-local `process`/optional `filesystem` authority model, and retain the exact source-backed hidden Core ActorRef/Bytes prototypes as runtime-only Prelude metadata so no duplicate standard identity is manufactured. The CLI deliberately grants no default Filesystem capability: launcher authority used to read source is not ambient application Filesystem authority, and I016 host filesystem policy remains explicit. No native Closure site is added; I018 stays 28 providers / 102 sites. I017-E3 and coordinating I017-E are CLOSED; I017-F is READY.

## 0.2.129-SNAPSHOT

- Implement I011-19 host-neutral remote ActorRef communication transport state: one internal route SPI carries the existing logical message snapshot while preserving ActorRef identity and exposes only delivery knowledge needed by Core (pending, accepted, known pre/post-acceptance failure, cancellation, completion, or acceptance uncertainty).
- Route ActorRef send/request through that SPI when present: SendOperation cancel remains true only for known pre-acceptance cancellation and retry is valid after terminal failure or delivery uncertainty; request transport uncertainty, including cancellation when non-acceptance can no longer be proved, fails with RequestOutcomeUncertain.
- Re-snapshot normal remote request replies at the caller Actor boundary so a transport adapter cannot introduce cross-Process mutable references. No wire format, endpoint syntax, discovery API, timeout/failure detector, transport selector, or new native Closure boundary is introduced; B004 remains limited to public Group/GroupRef acquisition/discovery.

## 0.2.128-SNAPSHOT

- Implement I017-E2 RootActor bootstrap-local authority provisioning. The unique RootActor's canonical initial module receives exactly one local `process` slot before its first source expression; the value is a fresh Actor-local Process capability proxy delegating to the source-backed `Process` prototype. The initial module still enters the Actor-local cache before source execution, so recursive imports observe the same partially initialized module and its already-provisioned bootstrap locals.
- Add a bootstrap-stable optional default Filesystem grant to `ProtosProcessRuntime`: the host either constructs the Process with one `ProtosFilesystemValue` or with no grant. When granted, the RootActor initial module receives the exact capability in a local `filesystem` slot; when absent, no such slot exists. Process authority remains unable to recover Filesystem authority.
- Keep ordinary module import and non-root Actor bootstrap on the existing ambient-authority-free path. Imported modules receive no local `process`/`filesystem` slots, hosted Actors do not inherit them merely because they belong to the same Process, and explicit Process Actor-transfer/delegation remains the only Core path for giving another Actor Process authority. E2 adds no native Closure site, so I018 remains 28 providers / 102 sites. I017-E2 is CLOSED and I017-E3 becomes READY for standalone host/CLI capture and wiring.

## 0.2.127-SNAPSHOT

- Start the safe subdivision of I017-E with I017-E1. Add a source-backed, frozen, authority-free standard `Process` prototype to the Core prelude and install exactly the eight standardized synchronous accessors: `args`, `environment`, `stdin`, `stdinEncoding`, `stdout`, `stdoutEncoding`, `stderr`, and `stderrEncoding`. The prototype has no constructor/call surface and cannot recover a Process capability.
- Implement all eight accessors as one audited runtime/capability Closure-construction helper over the already-published I017-A/B/C/D1/D2 substrate. Accessors require an actual represented Process capability rather than duck typing or delegation masquerading, perform no host discovery or waiting, return canonical args/environment snapshots and already-established stream/Encoding bindings, fail on unavailable/invalid bootstrap state, and reject every existing proxy after the Process termination cutover.
- Keep RootActor moduleContext provisioning and host/CLI capture out of E1. I017-E is subdivided into E1 public Process accessors, E2 RootActor bootstrap-local `process`/optional `filesystem` provisioning and imported-module confinement, and E3 host/CLI bootstrap capture/wiring. Register the single new Process Closure construction site in I018, moving the audited boundary from 27 providers / 101 sites to 28 providers / 102 sites. I017-E1 is CLOSED and I017-E2 becomes READY.

## 0.2.126-SNAPSHOT

- Implement I011-18 final local/cross-Process ActorGroup conformance: prove GroupRef continuity across member replacement, pre-acceptance rerouting when a selected member's Process terminates, and no rerouting after concrete acceptance.
- Add deterministic request-loss and cancellation races spanning Process, Actor, Group, and communication boundaries: accepted Group requests lost to Process termination fail with RequestOutcomeUncertain, while READY-versus-cancel races produce exactly one legal pre/post-acceptance outcome and never duplicate handler execution.
- Record B004 for the remaining public Group/GroupRef acquisition/discovery API: the distributed-runtime spec closes identity/routing semantics but explicitly leaves exact Group/GroupRef API/syntax and any new public discovery API undefined. I011 remains IN_PROGRESS; genuinely remote transport/acceptance uncertainty and I017 reconciliation may continue independently.

## 0.2.125-SNAPSHOT

- Implement LIB001-A as ordinary distributable Protos modules `std:collections/set` and `std:collections/identity_set`, with variadic module-call construction over fresh open Map/IdentityMap state and canonical `key -> true` representation.
- Add initial `contains` and `size` library observations, preserve normal Map equality versus IdentityMap semantic-identity membership and first-representative insertion order through the existing keyed protocols, and add focal real-`std:` module conformance without introducing a runtime Set family, wrapper, prototype, or native boundary.

## 0.2.124-SNAPSHOT

- Implement I011-17 language-visible GroupRef communication surface: create the hidden source-backed GroupRef delegation prototype in Core bootstrap and install exactly `send` / `request`, with no `stop`, `termination`, controller, Authority, broadcast, or acquisition selector.
- Reuse the already-audited Actor communication native construction helpers to create distinct ActorRef/GroupRef send/request Closures; receiver-directed dispatch selects concrete ActorRef or local ActorGroup routing while preserving synchronous whole-graph snapshot, caller Actor identity, SendOperation cancel/retry, Future reply transfer, and RequestOutcomeUncertain semantics from I011-8/9/16.
- Keep the I018 Java native construction-site inventory unchanged: ProtosStandardActorProtocol remains at eight sites and the repository-wide provider/site totals do not grow. Public/distributed Group acquisition and genuinely remote transport/acceptance uncertainty remain outside this slice.

## 0.2.123-SNAPSHOT

- Implement I017-D2 stable Process-bootstrap associations between each independently optional stdin/stdout/stderr byte binding and its host-selected immutable Encoding descriptor. Portable and explicitly host-provided Encoding descriptors from I015-A are both valid association values.
- Record each association exactly once as AVAILABLE, UNAVAILABLE, or INVALID: an available byte stream requires one Encoding; an unavailable stream requires no Encoding; any availability/Encoding mismatch becomes stable invalid bootstrap configuration rather than triggering a later host lookup, inferred default, alias lookup, or codec discovery.
- Keep D2 internal and synchronous: it adds no public Process selector yet, no Future/suspension, no text wrapper, no stream capability enlargement, and no Java native-Closure construction site. The audited I018 boundary remains exactly 27 providers / 101 sites. I017-D2 is CLOSED and I017-E becomes READY.

## 0.2.122-SNAPSHOT

- Implement I015-A standard Encoding semantic family with a source-backed frozen `Encoding` prelude factory/prototype and exactly the four mandatory portable descriptors: `Encoding.UTF8`, `Encoding.UTF16LE`, `Encoding.UTF16BE`, and `Encoding.Latin1`; no String-name constructor, alias registry, or implicit codec discovery is introduced.
- Implement strict synchronous one-shot `encoding.encode(String) -> Bytes` and `encoding.decode(Bytes) -> String` with exact semantic argument/receiver domains, fresh open Bytes identity on every successful encode including empty output, UTF-8/UTF-16 validity checks, default consumption of one initial matching BOM without encoding switching, no default BOM emission, and exact ISO-8859-1 Latin1 behavior. Malformed/unrepresentable conversion signals `EncodingError`.
- Add the explicit host-provisioning boundary for additional immutable Encoding descriptors and ordinary Actor/P transfer for Encoding as an authority-free immutable semantic value. Register the two-site Encoding representation bridge in I018, moving the audited boundary from 26 providers / 99 sites to 27 providers / 101 sites.
- Start the phased I015 implementation ledger. I015 remains IN_PROGRESS for streaming TextReader/readLine/TextWriter work, but the actual dependency needed by I017-D2 is now satisfied by I015-A Encoding; I017-D2 becomes READY.

## 0.2.121-SNAPSHOT

- Implement I011-16 local ActorGroup communication-operation foundation over I011-15 membership: GroupRef runtime send/request keep the original Actor-transfer snapshot while a live Group has zero eligible members, select one READY member when routing can progress, and wake pending routing when an INITIALIZING member becomes READY.
- Keep concrete-Actor acceptance authoritative: membership removal or selected-member pre-acceptance failure cancels/requeues only while cancellation is known to beat acceptance; accepted work is never transparently rerouted, Group termination fails only still-pre-acceptance work, and accepted request loss resolves as RequestOutcomeUncertain.
- Reuse the existing SendOperation cancel/retry contract through a shared runtime control interface, and keep every concrete/group SendOperation local by rejecting the common control type at Actor and P transfer boundaries, without adding a new native Closure site. Public Group acquisition/GroupRef selector installation and genuinely distributed transport ambiguity remain for later I011 slices.

## 0.2.120-SNAPSHOT

- Implement CLI004 standard-library module resolution for the official CLI with a reserved `std:<logical-name>` distribution namespace, logical relocation-independent `ModuleKey` identity, hidden `.protos` file mapping under `protos/lib/`, and explicit exclusion of bootstrap `core/`.
- Keep `std:` absolute and non-shadowable: invalid, missing, non-standard, extension-bearing, traversal-like, or otherwise non-portable spellings fail through the existing Core import Error path with no user search-path fallback.
- Wire the resolver through the existing Core bootstrap host boundary without changing normative Module semantics, and close the LIB001 import/distribution prerequisite while leaving Collections implementation itself READY and not started.

## 0.2.119-SNAPSHOT

- Implement I017-D1 Process-local standard byte-stream bindings with independently optional stdin/stdout/stderr bootstrap availability, non-waiting repeated view materialization, one shared input-consumption or output-ordering/backpressure domain per binding, and distinct stdout/stderr domains even when a host reuses one backend object.
- Expose exact byte-direction capability shape through construction-only helpers: stdin views have only `read`; stdout/stderr views have only `write`; no backend property implicitly adds Closable, Flushable, File, seek, text, Encoding, shutdown, or other authority. Actor transfer of the deliberately Actor-safe standard-stream proxy rematerializes a fresh wrapper to the same logical binding; P rejects the live I/O authority.
- Integrate standard-stream operations with ordinary I014 Future/commitment and Actor I/O cancellation machinery, including a two-sided Process-termination admission check and zero-consumption restoration when a cancelled stdin read races a late backend data completion. Register the two-site Process-stream resource bridge in I018, moving the audited boundary from 25 providers / 97 sites to 26 providers / 99 sites. I015 is not yet CLOSED on this definitive baseline, so I017-D2 remains dependency-blocked.

## 0.2.118-SNAPSHOT

- Implement I017-C standardized read-only Environment bootstrap snapshots: one stable acquisition outcome and canonical semantic snapshot per Process; duplicate-equivalent native names are rejected at establishment under the represented host name-identity rules; later host mutation cannot alter the captured mapping.
- Implement exact `get`/`contains` query semantics with lossless native-name representability checked before lookup, native name identity preserved, absent valid names distinguished from invalid queries, and value decoding deferred so `contains` can report an existing entry whose value is not portable Unicode while `get` fails only when that value is selected.
- Implement polymorphic `Environment.each` with callback callability validation before whole-snapshot `(String,String)` representability validation, zero callbacks on representation failure, canonical lexicographic Unicode-scalar name ordering, and receiver result on success; Environment is immutable, outside the required Core prelude and not a Map subtype.
- Allow ordinary Actor/P transfer of the immutable Environment snapshot with fresh destination semantic identity and per-transfer alias preservation, distinct from canonical re-acquisition through Process. Register the three-site Environment representation bridge in I018, moving the audited boundary from 24 providers / 94 sites to 25 providers / 97 sites. I017-C is CLOSED and I017-D1 becomes READY.

## 0.2.117-SNAPSHOT

- Implement I017-B canonical Process-argument bootstrap snapshots: one stable immutable snapshot per Process after complete one-time host capture, stable UNREPRESENTABLE outcome for invalid Unicode bootstrap data, exact `size`/zero-based `at`/polymorphic ordered `each`, no Array mutability, and no host argv re-read after establishment.
- Distinguish canonical acquisition from isolation transfer: repeated acquisition from the same Process returns the same semantic snapshot identity, distinct Processes remain distinct, while ordinary Actor and P transfer rematerialize immutable destination snapshots with fresh semantic identity and preserve aliases within one transfer graph.
- Register the three-site Process-argument representation bridge in the reviewed I018 boundary, moving the audited total from 23 providers / 91 sites to 24 providers / 94 sites. I017-B is CLOSED and I017-C becomes READY; the external I015 dependency remains only for I017-D2 Encoding accessors.

## 0.2.116-SNAPSHOT

- Implement I011-15 internal ActorGroup routing foundation: add one stable local ActorGroup runtime identity with explicit membership, live/terminated lifecycle, and routing eligibility that selects only READY concrete Actor members while allowing a live Group to have zero eligible members.
- Bind runtime-acquired GroupRef values to that concrete Group target without exposing mutable membership/controller state; independent acquisitions remain distinct GroupRef identities, while Actor-transfer rematerialization retains the same GroupRef identity and exact Group target.
- Keep member selection policy internal and make no Group-wide FIFO, broadcast, public acquisition, send/request, automatic reconciliation, Authority, controller, or transport promise in this slice. I011 remains IN_PROGRESS; Group communication operations and uncertainty are layered next.

## 0.2.115-SNAPSHOT

- Close I017-A by reconciling I017 with the concurrently published I011-14 Process-capability delegation substrate instead of duplicating it: runtime-provisioned Actor-local Process proxies, fresh explicit Actor rematerialization to the same logical Process authority, whole-graph alias preservation, authority-bearing descendant rebuilding, no authority amplification, and no Core P-transfer contract are already implemented and validated on current main.
- Record the phased I017 implementation plan. No public Process prototype, RootActor `process` bootstrap slot, Process accessor, host argument/environment/stream acquisition, or new native-Closure provider is added by I017-A itself. Process-termination rejection of actual accessor use will be enforced when the standard Process protocol becomes observable.
- Keep independent I017-B/C/D1 work unblocked while recording the external dependency I015 CLOSED -> I017-D2 for the standardized `stdinEncoding`/`stdoutEncoding`/`stderrEncoding` accessors.

## 0.2.114-SNAPSHOT

- Implement I011-14 runtime Process-capability Actor delegation: a host/runtime-provisioned represented Process proxy delegates to the future standard Process prototype, carries only authority into one existing logical Protos Process, and rematerializes as a fresh wrapper when explicitly transferred across an Actor boundary.
- Preserve whole-graph transfer aliasing for repeated references to one Process proxy while preventing a mutable wrapper alias across Actors; descendants whose delegation chain carries Process authority are rebuilt over the destination proxy, without manufacturing Filesystem, Node, Cluster, subprocess, or other authority.
- Keep Process outside isolated P transfer and leave the public Process prototype, RootActor bootstrap-local `process` slot, args/environment/standard-stream protocol, launcher provisioning, and Process I/O lifecycle to I017. No native-Closure provider or Core prelude surface is added; I011 remains IN_PROGRESS.

## 0.2.113-SNAPSHOT

- Close I016 Filesystem / File after final cross-slice validation of I016-A preflight/acquisition, I016-B positioned File semantics and lifecycle, I016-C append/aliasing, I016-D1 host-provisioned Filesystem.open integration, I016-D2 Actor/P authority-transfer boundaries, and I016-D3 deterministic integrated authority/race conformance.
- Preserve the post-D2/D3 I018 native boundary at the re-audited 23 providers / 91 native-Closure construction sites. A fresh current-main dependency audit, including the published I011-13 Process failure-domain / RootActor substrate, finds no unresolved Process-I/O implementation blocker; I016 is CLOSED and I017 Process I/O / bootstrap becomes READY for its own mandatory current-main normative/implementation audit before implementation.

## 0.2.112-SNAPSHOT

- Implement I011-13 internal Process failure-domain and RootActor failure-authority substrate: one Process owns exactly one RootActor plus its currently hosted Actors, Process termination requests termination of every hosted incarnation, and Process TERMINATED is reached only after Actor-required cancellation/cleanup has completed.
- Distinguish fatal Actor failure from graceful lifecycle termination. A non-root fatal Actor failure terminates only that incarnation under Core v0.1, while a RootActor fatal failure drives authoritative local Process termination without transferring the internal failure object to another Actor.
- Keep Actor.spawn creation inside the caller's already-bound local Process when such a Process runtime exists, while preserving the pre-existing standalone Actor path for unbound runtime/tests. No public Process capability, standard-stream provisioning, Group routing, replacement policy, Node/Cluster machinery, or OS-process termination API is introduced; I011 remains IN_PROGRESS.

## 0.2.111-SNAPSHOT

- Implement I016-D3 integrated Filesystem/File conformance: deterministic pre-commit cancellation and late-custody release, post-commit cancellation precedence, stable selected-resource binding across simulated namespace replacement, independent same-path opens with out-of-order completion, backend authority rejection without fallback, and descriptor-owned optional capability shape.
- Re-audit the complete post-D2 I018 native boundary and confirm it remains 23 providers / 91 native-Closure construction sites; strengthen the architecture guard with the explicit provider count and the invariant that Filesystem authority is absent from the Core prelude. I016-D4 becomes READY for final cross-slice validation and project-status/dependency closure.

## 0.2.110-SNAPSHOT

- Implement I016-D2 authority/transfer boundaries with explicit runtime markers for live File and host-provisioned Filesystem capabilities while keeping their language-visible ordinary-object protocols unchanged.
- Reject direct File/Filesystem authority and ordinary descendants carrying that authority at Actor transfer with `NonTransferableValue`, and at isolated P transfer with `NonParallelValue`; no proxy, reopen, handle duplication, ambient inheritance, or new native Closure provider is introduced. I016-D3 becomes READY for deterministic authority/race conformance and the final post-D2 I018 boundary re-audit.

## 0.2.109-SNAPSHOT

- Implement I016-D1 Filesystem open integration: host-provisioned open-only Filesystem capability, exact one/two-argument `open` bridge over the I016-A preflight/acquisition flow, and standard positioned/append File materialization through the I016-B/C File protocol with exact requested read/write/append authority.
- Tighten open options to the normative ordinary-object domain and document the backend authority contract for confinement, race-free selection, create/truncate commitment, stable selected-resource custody, and synchronous pre-commit cancellation cleanup. Register the single new resource/capability native bridge in the post-I018 boundary guard/inventory so the full suite remains architectural evidence. I016 remains IN_PROGRESS under the recorded D1-D4 completion plan.

## 0.2.108-SNAPSHOT

- Implement I011-12 GroupRef capability-identity foundation: add an opaque represented `GroupRef` value whose semantic reference identity is independent of Group identity, physical wrapper identity, and acquisition path.
- Integrate GroupRef with primitive semantic identity/identityHash and Actor-boundary transfer so repeated rematerializations preserve one capability identity and effective restriction descriptor, while independent acquisitions to the same Group remain distinct.
- Verify transferred GroupRef values remain valid `Map` and `IdentityMap` keys without exposing or copying mutable Group/controller/routing state. This slice intentionally adds no public Group acquisition, membership, routing, send/request, broadcast, Authority, or controller API; I011 remains IN_PROGRESS.

## 0.2.107-SNAPSHOT

- Implement I011-11 Actor-boundary keyed-collection transfer for `Map` and `IdentityMap`, preserving keyed insertion order, graph aliasing/cycles, local slots, and open/closed/frozen state while keeping the complete transfer atomic.
- Rebuild destination hash bookkeeping without invoking ordinary `hash`/`==` during snapshot formation: default identity-based Map keys receive the copied identity's hash, specialized recorded Map hashes are preserved, and IdentityMap entries receive the copied key's semantic `identityHash`.
- Align default `Object.hash` with the existing semantic `ProtosIdentity.identityHash` primitive so rematerialized identity-bearing capabilities such as `ActorRef` remain valid equal/hash keys. I011 remains IN_PROGRESS; Group/distributed routing, GroupRef/Process capability transfer, failure authority and RootActor/Process integration remain.

## 0.2.106-SNAPSHOT

- Close I018 Core self-hosting/bootstrap minimization after an exhaustive current-main inventory of every production `ProtosClosureValue.nativeClosure(...)` provider. The final boundary contains 90 native-Closure construction sites across exactly 22 Java providers, all classified as irreducible host execution/control, semantic-value representation, concurrency/runtime, or resource/capability bridges; no remaining standard slot is classified as faithfully source-expressible.
- Add `docs/project/CORE_NATIVE_BOUNDARY.md` as the non-normative maintenance inventory and `ProtosCoreNativeBoundaryArchitectureTest` as an executable guard over the exact provider set/counts, helper-backed runtime selector surfaces, migrated Object/Integer/Float source provenance, internal Bytes/ActorRef/SendOperation surfaces, and the Core-bootstrap direct-allocation boundary.
- Mark I018 CLOSED and lift the temporary coordination pause that held I016 at the already-published I016-C state. I016 may resume from I016-D after re-auditing the then-current `origin/main`. No normative specification changes are made.

## 0.2.105-SNAPSHOT

- Implement I011-10 graceful Actor lifecycle: add public `ActorRef.stop()` and `ActorRef.termination()`, establish the irreversible stop cutover without a stop Future, and provide fresh caller-local termination observation Futures tied to one concrete incarnation.
- Move Actor termination cancellation to the TERMINATING cutover: cancel all live Actor-local tasks (including detached work), pending Actor-originated non-task Futures and I/O operations, suppress accepted-but-undispatched work/bootstrap after the cutover, and reach TERMINATED only after required task cancellation unwind completes.
- Preserve producer commitment/acceptance boundaries, exact request uncertainty, non-preemption of already-running non-suspending turns, and independent/cancellable termination observations. I011 remains IN_PROGRESS; distributed/Group routing, specialized capability transfer, failure authority and RootActor/Process integration remain for later slices.

## 0.2.104-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the internal standard `ActorRef` and `SendOperation` delegation prototypes in distributable `protos/lib/core/actor.protos` rather than allocating those prototype identities inside `ProtosStandardActorProtocol`.
- Supply the exact source-created prototypes to the Actor runtime installer, validate direct-`Object` parentage plus open/empty source shape before mutation, install only the existing native communication/lifecycle bridges (`send`, `request`, `cancel`, `retry`), freeze the exact supplied objects, and remove the construction-only helper bindings before prelude construction.
- Extend Actor API regression coverage for the exact supplied prototype identities/surfaces and update deterministic send/request fixtures to inject their prototype identities explicitly. `ActorRef` and `SendOperation` remain absent as public prelude bindings. No normative specification changes are made; I018 remains open only for the final exhaustive native-boundary inventory/architectural guard, and I016 remains frozen at I016-C.

## 0.2.103-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard default `Object.==` body from Java into distributable `protos/lib/core/object.protos`.
- Define the source-backed body as `this === other`, delegating exactly to Protos primitive semantic identity rather than host object identity; reuse the existing isolated frozen Object-source capture context and narrow symbolic-selector installation bridge, and remove the duplicated native equality closure from `ProtosStandardObjectProtocol`.
- Extend bootstrap provenance coverage so `Object.init`, `Object.==`, and `Object.!=` are all source-backed and share the isolated frozen source context. Existing default-equality/non-identity conformance remains focal. No normative specification changes are made, I018 remains open for the final native-bridge inventory/guard, and I016 remains frozen at I016-C.

## 0.2.102-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard frozen-prelude bindings object in distributable `protos/lib/core/prelude.protos` instead of allocating and populating that object slot-by-slot in `ProtosCoreBootstrap`.
- Preserve the exact existing prelude surface and identities, including the complete validated Error taxonomy, direct `Context` parentage, absence of the construction-only `Bytes` binding, and shallow final freeze. Remove the Java-side Error-taxonomy export helper while retaining host-side topology validation.
- Add focused regression coverage for the exact prelude binding set and an architectural guard preventing `ProtosCoreBootstrap` from reintroducing direct `preludeBindings.createLocalSlot(...)` construction. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.101-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard `Float.negated()` body from Java into distributable `protos/lib/core/float.protos`.
- Express Float negation as `(0.0 - 1.0) * this`: constructing semantic binary64 `-1.0` through native subtraction avoids recursive use of `negated`, and native Float multiplication then validates the original receiver while producing exact binary64 sign inversion, including `+0.0`/`-0.0`, infinities, subnormals, and Core NaN semantics.
- Add focused provenance and receiver-domain regression coverage proving `Float.negated` is source-backed while the binary64 `*` representation primitive remains native-backed. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.100-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard ordinary `Integer.%` derived body from Java into distributable `protos/lib/core/integer.protos`.
- Express `%` as `(0 + this).mod(argument)`: the native standard Integer `+` on semantic zero validates the original receiver before any `mod` dispatch, then the existing native `mod` primitive supplies the normative truncation-toward-zero remainder semantics. Java retains only the narrow symbolic-selector installation bridge from the temporary source name to `%`.
- Add provenance and adversarial receiver-domain regression coverage proving `%` is source-backed, `mod` remains native-backed, the temporary source slot is removed, and an incompatible Integer-delegating object cannot bypass standard receiver validation by overriding `mod`. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.99-SNAPSHOT

- Implement I011-9 concrete-Actor `ActorRef.request(selector, arguments...)`: reuse the I011-7/I011-8 delivery boundary, return a fresh caller-domain Future, transfer the normal handler result back across the Actor boundary, and never flatten or adopt a destination-local Future.
- Complete direct concrete-Actor accepted-work loss notification: accepted-but-undispatched work lost at termination becomes post-acceptance failure; request loss/fatal handler failure maps to a fresh caller-domain `RequestOutcomeUncertain`, non-transferable replies fail with `NonTransferableValue`, and cancellation preserves the pre/post-acceptance distinction. Distributed/Group routing uncertainty, lifecycle observation/stop, specialized transfers, and final Actor cleanup remain for later I011 slices.

## 0.2.98-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard ordinary `Integer.negated()` body from Java into distributable `protos/lib/core/integer.protos` as ordinary Protos behavior (`0 - this`).
- Keep exact unbounded Integer subtraction host-backed as the representation primitive; using semantic Integer zero as the subtraction receiver preserves the existing Integer receiver-domain rejection even when an incompatible ordinary object merely delegates to `Integer`.
- Add focused provenance and receiver-domain regression coverage proving `negated` is source-backed while `Integer.-` remains native-backed. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.97-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the internal standard `Bytes` factory/prototype used by buffered byte wrappers from distributable `protos/lib/core/bytes.protos` instead of allocating that standard identity in Java.
- Preserve the existing native Bytes constructor/indexing/mutation/snapshot/parallel-region protocol unchanged, including factory-receiver parentage of produced Bytes values; remove the construction-only `Bytes` binding before the frozen standard prelude is built because Core v0.1 does not require a `Bytes` prelude binding.
- Add a bootstrap regression that `Bytes` remains absent from the standard prelude. I016 remains frozen at I016-C.

## 0.2.96-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard frozen-prelude `import` facility from distributable `protos/lib/core/import.protos` instead of allocating that public standard identity in Java.
- Keep module-specifier validation, host resolution, canonical ModuleKey handling, Actor-local caching, cache-before-execute, cycles, initialization failure, and retry semantics in the existing runtime; Java now installs only the `call` primitive bridge into the exact source-created import object and freezes that same object.
- Add focused regression coverage for direct-`Object` parentage, exact `call` surface, native bridge provenance, and installer identity preservation. Restore the required full APL Part 5 notice on the touched import protocol/module runtime test sources. I016 remains frozen at I016-C.

## 0.2.95-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard `BufferedReader` and `BufferedWriter` frozen-prelude factory/prototype objects from distributable Core source instead of allocating those public standard identities in Java.
- Keep buffered byte construction, capability validation, borrowing/ownership, Future, buffering, and lifecycle behavior host-backed; Java now installs only the existing `call`/`owning` primitive bridges into the exact source-created factory objects and freezes those same objects.
- Add focused regression coverage for direct-`Object` parentage, exact `call`/`owning` surface, preserved supplied factory identity, and native bridge provenance. Restore the required full APL Part 5 notice on the touched buffered protocol/test sources. I016 remains frozen at I016-C.

## 0.2.94-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard `Actor` prelude entry object from distributable `protos/lib/core/actor.protos` instead of allocating that public standard object in Java.
- Keep `Actor.spawn` and `Actor.current` as host-backed primitive bridges, install them into the exact source-created Actor object, validate its direct-`Object` parent and empty/open source shape, and freeze that same object without changing Actor semantics.
- Add focused regression coverage that the Actor protocol installer preserves the supplied source object identity and contributes only native `spawn`/`current` behavior. I016 remains frozen at the already-published I016-C state.

## 0.2.93-SNAPSHOT

- Implement I011-8 public concrete-Actor `ActorRef.send(selector, arguments...)`: require an exact semantic String selector, form the complete Actor-boundary snapshot synchronously before admission, and dispatch accepted work against the destination Actor's stable behavior/message environment while ignoring the handler's normal result.
- Add the local identity-bearing SendOperation with exactly `cancel()` / `retry()`: cancellation succeeds only when known pre-acceptance cancellation wins; retry is explicit after known terminal delivery failure, creates a fresh operation identity, and reuses the original logical snapshot without source re-evaluation. SendOperation is local/non-transferable. Request/reply and distributed/accepted-work-loss uncertainty remain for later I011 slices.

## 0.2.92-SNAPSHOT

- Start I018 Core self-hosting/bootstrap minimization by moving standard `Object.init` and the body of standard `Object.!=` out of Java and into distributable `protos/lib/core/object.protos`.
- Load those source-backed Object Closures in an isolated frozen bootstrap context, promote them through a narrow host selector bridge, and prevent the process-global root `Object` from retaining the main Core-construction bindings.
- Keep observable Object initialization and inequality semantics unchanged; remaining Java-installed standard protocols are left for later bounded I018 slices rather than expanded. I016-C was already published before this slice; I018 does not advance I016 further.

## 0.2.91-SNAPSHOT

- Implement I011-7 concrete-Actor pre-acceptance delivery admission/backpressure substrate: keep pending logical operations outside the bounded accepted mailbox, wake admission deterministically as capacity is released, and preserve known acceptance/cancellation boundaries.
- Use a FIFO pending discipline to satisfy same-sender FIFO across backpressure and Core weak admission fairness without exposing a new public queue or total-order contract. Public ActorRef send/request, SendOperation retry/reply/uncertainty, specialized transfers, and lifecycle observation remain for later I011 slices.

## 0.2.90-SNAPSHOT

- Implement I016-C append-mode File placement and aliasing semantics: every non-empty append write selects the then-current EOF at the operation's contribution boundary, successful empty append leaves the logical cursor unchanged, failed writes preserve the exact contributed-prefix aftermath, and an earlier seek never turns append into positioned output.
- Define the backend AppendWritableResource contract as an underlying-resource-wide atomic append-placement boundary shared across distinct File/resource aliases, preventing overlap/interleaving while leaving concurrent alias order nondeterministic. Add deterministic cancellation, Actor-termination, ordering, capability-honesty, and cross-alias conformance coverage. Filesystem authority/confinement and public open materialization remain for the final I016 slice.

## 0.2.89-SNAPSHOT

- Implement I016-B positioned standard File capability: stable per-File logical cursor beginning at zero, explicit positional read/write backend boundary, ordered read/write/position/seek/seekToEnd/size/truncate/sync operations, exact failed-write prefix position aftermath, bounded write snapshot admission, and capability-honest stable protocol shape.
- Integrate File Closable lifecycle with I014 commitment and Actor-termination cancellation machinery; close-cutover now invokes the cancellation hook of accepted uncommitted I/O before resource release begins, while committed operations keep their normal aftermath. Raw File does not invent Flushable, Filesystem.open is still not publicly installed, and append/confinement host integration remain for later I016 slices.

## 0.2.88-SNAPSHOT

- Implement I011-6 internal bounded Actor mailbox ownership and READY-gated implicit event-loop dispatch; accepted message turns remain finite, FIFO in accepted order, and undispatched while the destination is INITIALIZING.
- Add automatic Actor-local scheduler wakeups and a weak-fair cross-Actor scheduler that selects one non-preemptive segment per Actor turn, permits independent Actors to use different carriers, and never executes two Protos segments concurrently in one Actor incarnation. I011 remains IN_PROGRESS; pre-acceptance admission/backpressure/FIFO fairness and the public send/request operation semantics remain for later slices.

## 0.2.87-SNAPSHOT

- Implement I016-A Filesystem open preflight/acquisition substrate: exact invocation-time capture of local standard open options, deterministic invalid-combination rejection before backend authority, host-neutral asynchronous acquisition, and independent open dispatch without an implicit Filesystem/Path FIFO.
- Reuse the established I/O commitment and Actor-termination cancellation machinery so pre-commit cancellation contributes no portable filesystem effect, committed create/truncate effects cannot be rewritten as cancelled, and a result-only open that loses cancellation releases untransferred backend custody. No public Filesystem/File surface is installed by this foundational slice; I016 remains IN_PROGRESS.


## 0.2.86-SNAPSHOT

- Implement I011-5 public Actor prelude surface with exactly `spawn` and `current`; perform exact semantic-String validation, one creator-side canonical module resolution, and the I011-4 atomic initialization-vector transfer before the creation cutover.
- Kick off I011-3 destination-local bootstrap only after the cutover and return the stable ActorRef without waiting for READY; preserve current-ActorRef identity and same-incarnation termination on later bootstrap failure. I011 remains IN_PROGRESS; mailbox/send/request, stop/termination observation, RootActor integration, distributed routing, and remaining specialized transfers stay outside this slice.

## 0.2.85-SNAPSHOT

- Implement I011-4 Actor graph snapshot/value-transfer foundation: add one Actor-specific atomic graph-copy boundary with shared-operation memoization, preserving aliases and cycles across roots while copying transferable scalar values, ordinary object state, Arrays, Bytes state, and Paths.
- Rematerialize ActorRef capability wrappers without retargeting or copying Actor state; preserve ordinary delegation/local-slot/mutation state for copied objects; reject Closure, Future, execution-context, ByteRegion, unknown host/runtime values, and not-yet-integrated keyed collections with the standard NonTransferableValue occurrence before any snapshot is exposed. I011 remains IN_PROGRESS and no Actor public API is installed by this slice.

## 0.2.84-SNAPSHOT

- Implement I011-3 Actor bootstrap and behavior cutover: bind each concrete Actor to its existing execution domain, expose only a runtime-local current-ActorRef substrate, load bootstrap code by an already-canonical ModuleKey in the destination Actor-local module cache, require an exact local bootstrap binding, invoke it with already-transferred arguments, and install the exact ordinary-object result before the READY cutover.
- Preserve canonical module identity without destination re-resolution, Actor-local module instances, stable behavior identity after READY, and initialization-failure termination. Public Actor.spawn/current remain deliberately uninstalled until synchronous graph transfer and the remaining runtime prerequisites are implemented; mailbox/admission, send/request, stop/termination monitoring, GroupRef, and distributed routing remain outside this slice.

## 0.2.83-SNAPSHOT

### Fixed
- Implement the normative standard Number ordering selectors `<`, `<=`, `>`, and `>=` as ordinary Number-owned Closure-valued behavior inherited by Integer, fixed-width Integer, and Float values.
- Compare numeric families without promotion or coercion, including exact arbitrary-precision Integer versus finite binary64 Float ordering, signed zero, infinities, and unordered NaN behavior.
- Add regression coverage for the official recursive factorial form in a persistent multiline REPL session.

### Notes
- Standard ordering rejects non-Number arguments and incompatible original receivers with a Protos Error.
- No parser, multiline-REPL, arithmetic compatibility, or normative specification behavior is changed.

## 0.2.82-SNAPSHOT

- Implement I011-2 ActorRef capability semantics: represent ActorRef as an opaque communication capability permanently bound to one Actor incarnation and add explicit Actor-boundary rematerialization that creates fresh wrappers while preserving semantic identity, `identityHash`, delegation parent, and the original target.
- Keep the mutable Actor target outside Protos-visible state and preserve references after termination without retargeting replacements. This slice intentionally does not add Actor.spawn/current, bootstrap/READY behavior installation, mailbox/admission, graph snapshot traversal, send/request, stop/termination monitoring, GroupRef, or distributed routing.

## 0.2.81-SNAPSHOT

- Implement I011-1 Actor incarnation identity/lifecycle foundation: add an explicit runtime-local immutable incarnation identity, centralized race-safe `INITIALIZING`/`READY`/`TERMINATING`/`TERMINATED` state machine, and a semantic `ActorRef` value permanently bound to one incarnation.
- Reuse the existing Actor execution domain and Actor-local module state, preserve `ActorRef` semantic identity and `identityHash` across rematerialized wrappers, and add deterministic lifecycle/concurrency focal tests. I011 remains open; this slice does not add spawn/current, mailbox, send/request, backpressure, transfer, monitoring, graceful-stop policy, GroupRef, or distributed routing.

## 0.2.80-SNAPSHOT

### Fixed
- Implement CLI003 multiline REPL input so a complete JLine bracketed-paste payload is compiled and evaluated once instead of being split into per-line evaluations.
- Accumulate interactive and stream REPL source while the parser reports an unexpected end of source, preserving nested closures, object/block source, blank lines, and persistent top-level context until the syntactic unit is complete.
- Keep invalid non-EOF syntax recoverable without poisoning the next REPL input, and make Ctrl-C discard the entire pending interactive unit.

### Notes
- Parser completeness uses the actual EOF token carried by `ParseError`; CLI003 does not count delimiters or introduce a separate syntax heuristic.
- JLine history continues to store non-blank physical lines independently, while evaluation now operates on the complete source unit.
- File execution and `-e` continue to use the existing source compiler path unchanged, and the Truffle `sun.misc.Unsafe` warning remains outside CLI003.
- No normative specification revision is changed.

## 0.2.79-SNAPSHOT

- Complete I014-G buffered byte-I/O lifecycle/cancellation conformance: close cutover now terminates accepted-but-uncommitted adapter operations with fresh lifecycle failures, active cancellation is propagated to lower Futures without rolling back committed effects, close retains its activation across asynchronous completion, in-flight buffered flushes are reconciled without duplicate propagation, and owning close waits for the owned target close while preserving a primary wrapper-finalization failure.

## 0.2.78-SNAPSHOT

- Implement I014-F standard buffered byte I/O: frozen `BufferedReader`/`BufferedWriter` factories with borrowing/owning forms, ordered bounded buffering, transparent/resumable EOF, recursive flush propagation, deterministic wrapper close ownership/failure handling, permanent output-side failure after ambiguous propagation, fresh standard Futures, and focal conformance tests.

## 0.2.77-SNAPSHOT

- Implement I014-E standard byte I/O directional shutdown: capability-honest `ReadShutdown`/`WriteShutdown`, irreversible directional cutovers, idempotent fresh-Future lifecycle observation, pending-read cutover, output-frontier ordering, cancellation/commitment behavior, and focal tests.

## 0.2.76-SNAPSHOT

- Implement I014-D standard byte I/O durability: explicit `Syncable.sync()` capability, ordered durability frontiers integrated with I014-C sequence-state ordering, and cancellation/commitment semantics that prevent post-commit sync from becoming cancelled.

## 0.2.75-SNAPSHOT

- Implement I014-C standard byte I/O positioning: `Flushable.flush`, `ByteSeekable` position/seek operations, `ByteSized.size`, and failure-atomic non-extending `Truncatable.truncate`, integrated with I014-B transfer ordering and Future cancellation/commitment semantics.


## 0.2.74-SNAPSHOT

- Implement I014-B standard sequential byte transfer: ordered asynchronous `ByteReadable.read(maxBytes)` and `ByteWritable.write(bytes)`, Future-based validation/errors, EOF/partial-read behavior, write snapshotting, bounded admission, cancellation/commitment integration, and focal tests.

All notable changes to the Protos implementation project will be documented in this file.

For specification changes, see [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## 0.2.73-SNAPSHOT

- Implement I010 isolated parallel execution: Closure.parallel, deterministic Array parallelMap/parallelFilter/parallelFindIndex/canonical parallelReduce/stable parallelSort, and exclusive Bytes/ByteRegion parallelRange.
- Add bounded lazy process-local P carriers, graph isolation and per-P Closure relowering, nested-P helping, Future structured ownership/cancellation, deterministic failure precedence, and atomic ByteRegion publication.
- Preserve caller standard-prototype mutability while restricting direct P physical sharing to already-frozen standard identities. No public P/Task/scheduler value, parallelEach, I011, or I014 surface is introduced.

## 0.2.72-SNAPSHOT

- Implement I009 — Future/Task: standard Future prototype/value state machine, `closure.future()`, suspendable `value()`, cancellation, `then()`, deterministic `Future.all(...)`, adoption/flattening, and `detach()`.
- Integrate pending Future waiters with the I009B evaluator bridge and I009A Actor-local Task scheduler, including race-safe register/suspend handoff, multiple waiters, cancellation cleanup, exact resume, and first-terminal-wins.
- Preserve Future/result/Error identity, semantic null, fresh Cancelled observations, Actor-domain isolation, continuation non-reentrancy, and deterministic aggregate ordering.
- Add focal end-to-end tests across real Truffle suspension/resumption plus protocol/state-machine coverage. No normative specification revision is changed.

## 0.2.71-SNAPSHOT

- Implement I009B evaluator suspension/resumption bridge for ordinary Truffle-backed Protos execution using Actor-local per-task continuation state.
- Preserve completed expression effects across cooperative suspension while rebuilding only the host Java/Truffle call stack, including nested Closure invocation activation and return-home state.
- Add explicit cancellation-aware wait cleanup and deterministic end-to-end coverage for peer-task progress, exact resume values, side-effect non-repetition, nested evaluation/non-local return, cancellation wake-up, and no double resume.
- No normative specification revision is changed.

## 0.2.70-SNAPSHOT

- Implement I009A internal Task/Actor execution infrastructure: Actor-local cooperative FIFO runnable queues, race-safe Task state transitions, suspension/resume, cooperative cancellation wake-up, and structured parent/child ownership.
- Keep cancellation of a suspended Task independent from its observed wait dependency so Future.value() can later wake for cancellation without cancelling or completing the observed Future.
- Add deterministic focal runtime tests for Actor isolation, dispatch ordering, duplicate resume exclusion, cancellation boundaries/races, structured ownership, and terminal transition safety.

## 0.2.69-SNAPSHOT

- Implement I008 standard module runtime semantics: exact semantic-String `import(specifier)`, host-produced canonical `ModuleKey`, Actor-local module caches, cache-before-execute cycles, single evaluation, failure eviction, and retry.
- Execute imported module source exclusively through `ProtosSourceCompiler`; module instances are their ordinary `moduleContext` objects and partially initialized state is directly observable during cycles.
- Translate host resolver/source/compiler failures to Core `Error` signaling without exposing Java exceptions, while leaving host resolution policy and Filesystem APIs outside I008.
- Add focal module conformance coverage for semantic membership, canonical identity, Actor isolation, cycles, cache behavior, retry, and host-error translation.

## 0.2.68-SNAPSHOT

- Implement Standard Bytes semantics: add receiver-owned mutable standard Bytes state and an explicitly installable standardized Bytes factory/prototype without adding a mandatory Core-prelude binding.
- Add exact octet validation (semantic Integer 0..255), zero-based size/at, replacement-only atPut, open-only add/removeAt, and ascending-snapshot each through ordinary polymorphic invocation.
- Preserve identity-bearing defaults for ==, hash, === and identityHash; byte contents are not traversed for equality or hashing.
- Add focal I012 conformance tests for construction, membership, lookup, numeric boundaries, mutation state, snapshot iteration, identity/equality/hash, bracket indexing, and Core Error paths.
- Do not add String/Encoding/Text I/O, UTF-8 coercion, slicing, concatenation, iterators, Array conversion, Filesystem, Set, or IdentitySet behavior.

## 0.2.67-SNAPSHOT

### Added
- Implemented I013 Standard Path with portable represented values and the normative `relative`, `rooted`, `child`, and `parentComponent` construction protocol.
- Added filesystem-independent structural equality/hash while preserving D037 ordinary individual identity and IdentityMap behavior.
- Added focused conformance coverage for construction, lookup, receiver domains, frozen-prelude binding, D037, and absence of host path parsing.

### Notes
- No filesystem authority, host normalization, realpath, separator interpretation, or String-to-Path coercion is introduced.
- No normative specification revision is changed.

## 0.2.66-SNAPSHOT

### Fixed
- Changed interactive REPL history handling so a bracketed paste containing multiple complete lines is stored as one JLine history entry per line, matching the already independent per-line evaluation behavior.
- Added focused coverage for multiline history splitting and ordering.

### Notes
- History remains implemented by JLine; Protos does not introduce a separate readline/history subsystem.
- Structured syntactic multiline parsing remains out of scope: pasted complete lines are still evaluated independently.
- No normative specification revision is changed.

## 0.2.65-SNAPSHOT

### Fixed
- Corrected the CLI002 multiline-paste regex Java string literal so the regex engine receives `\R` through a valid Java string literal.
- This is a compile-fix only; REPL semantics and the previously added line-by-line paste handling are unchanged.
- No normative specification revision is changed.

## 0.2.64-SNAPSHOT

### Fixed
- Fixed CLI002 bracketed-paste handling so a JLine `readLine` result containing multiple complete lines is split and evaluated sequentially in the same persistent REPL session.
- Added a focused regression test for the actual JLine-style multiline paste payload, rather than only testing newline-separated stream input.
- Added `--enable-native-access=ALL-UNNAMED` to the launcher to suppress the JDK restricted-native-access warning emitted when JLine initializes its native terminal support.

### Notes
- This does not add structured syntactic multiline parsing; each pasted complete line remains an independent evaluation.
- The pre-existing Truffle `sun.misc.Unsafe` warning is unchanged and remains outside CLI002.
- No normative specification revision is changed.

## 0.2.63-SNAPSHOT

### Added
- Added CLI002 interactive terminal UX using JLine 3.30.6 for readline-style editing, session history, bracketed paste, Ctrl-C cancellation, and clean Ctrl-D exit.
- Preserved the stream-based REPL path for automated tests and non-terminal input; pasted complete lines remain independent evaluations with one persistent Protos context.

### Notes
- CLI002 changes terminal input only; the existing parser/canonical/lowering/Truffle evaluation pipeline is unchanged.
- Structured syntactic multiline input is not added because CLI002 does not introduce parser-completeness heuristics.
- Protos Error rendering remains minimal (`Error: <object>`); Truffle/JDK `sun.misc.Unsafe` warnings are outside CLI002.
- No normative specification revision is changed.

## 0.2.62-SNAPSHOT

### Added
- Added CLI001 basic executable CLI with file execution, `-e`, help/version, and persistent-context REPL.
- Added non-normative value rendering, focused CLI/REPL tests, `bin/protos`, and executable shaded-JAR packaging.

### Notes
- CLI001 reuses the normal parser/canonical/lowering/Truffle pipeline.
- Top-level script arguments remain unavailable because Core `args` is Closure-invocation-only.
- No normative specification revision is changed.

## 0.2.61-SNAPSHOT

### Added
- Implemented I006 IdentityMap with semantic identity hashing plus primitive `===`.
- Completed fixed-width Integer identity and identity-hash coherence.
- Added IdentityMap conformance coverage.

### Notes
- Standard Map remains on `hash` plus `==`; Set and IdentitySet remain out of scope.
- No normative specification revision is changed.

## 0.2.60-SNAPSHOT

### Added
- Implemented I005 Standard Map: Map(), at, atPut, containsKey, remove, size, insertion-order each, semantic receiver-domain checks, recorded hashes and query-key equality.
- Added Map-required standard hash behavior for ordinary objects, Numbers and Strings.
- Set remains explicitly out of scope.

### Notes
- No normative specification revision is changed.

## [0.2.59-SNAPSHOT] - 2026-09-04

### Added

- I003 — implemented the standard semantic String prototype and represented-value
  delegation through the generalized I002 lookup bridge.
- Added standard String `size`, `at`, bracket-read, and binary `+` behavior with
  receiver-domain validation and Protos Error signaling.
- Pinned ICU4J 78.1 and require Unicode 17 data so `String.size` and `String.at`
  use Unicode 17.0.0 default extended grapheme clusters rather than host/JDK text
  segmentation.
- Added conformance coverage for empty/ASCII/Unicode/supplementary Strings,
  exact-scalar equality/identity, fixed-width Integer indexing, invalid indexes,
  concatenation, and non-membership by delegation.

### Notes

- String semantic identity remains exact Unicode-scalar-sequence identity; no
  normalization, coercion, encoding, Bytes, module, or text-I/O behavior is added.
- No normative specification change is introduced.
- Maven remains intentionally outside the installer and is run manually.
- Project implementation version is `0.2.59-SNAPSHOT`.

## [0.2.58-SNAPSHOT] - 2026-09-04

### Fixed

- I007 follow-up — SlotNotFound test-prelude compatibility: updated the lightweight
  test prelude to expose the normative `SlotNotFound -> Error` prototype and
  corrected legacy lookup/member/message/bare-assignment assertions to expect
  fresh `SlotNotFound` occurrences.
- Repaired the malformed implementation version `0.2.57-SNAPSHOT-SNAPSHOT`
  produced by the original I007 installer.

### Notes

- No normative specification change is introduced.
- Maven remains intentionally outside the installer and is run manually.
- Project implementation version is `0.2.58-SNAPSHOT`.

## [0.2.57-SNAPSHOT] - 2026-09-04

### Added

- I007 — Core error infrastructure: installed the closed normative Core Error
  prototype taxonomy and exact parent relations.
- Added typed fresh-occurrence factories and exact-object signaling through
  `ProtosCoreErrors`.
- Installed `Error.signal()` with receiver/arity validation, no implicit
  condition-designator coercion, and exact signaled-object preservation.
- Added focused tests for freshness, hierarchy, `InvalidReturn`, signaling,
  incompatible receivers, and host/program-error separation.

### Fixed

- Normative lookup absence now creates fresh `SlotNotFound` occurrences rather
  than generic `Error` occurrences.

### Notes

- Future, Actor, parallel, I/O, and filesystem execution remain reserved to
  their subsystem implementations; I007 only exposes the standard Core
  prototypes/factory infrastructure they require.
- `Error.handle(body, handler)` is not faked with a Java catch. The current
  runtime does not yet contain the handler-frame/unwind machinery required to
  implement the normative deactivation-before-cleanup rule correctly.
- No normative specification change is introduced.
- Project implementation version changed from `0.2.56-SNAPSHOT` to `0.2.57-SNAPSHOT`.

## [0.2.56-SNAPSHOT] - 2026-09-04

### Changed

- I002 — uniform represented-value lookup: introduced a small internal
  `ProtosRepresentedValue` bridge that supplies only the immediate delegation
  parent used by ordinary lookup for specialized runtime representations.
- Migrated Integer, Float, fixed-width Integer, canonical Boolean, and canonical
  `null` lookup onto the uniform bridge, removing the growing represented-value
  `instanceof` chain from `ProtosValueLookup`.
- Unified activation receiver lookup through `ProtosValueLookup`, preserving
  ordinary-object lookup while allowing represented values whose normative
  parent does not require the Core prelude to participate in the same path.
- Added regression coverage for ordinary lookup, numeric/fixed-width lookup,
  Boolean/null delegation, original-receiver binding, receiver-domain rejection,
  bridge extensibility, and polymorphic invocation.
- Project implementation version changed from `0.2.55-SNAPSHOT` to
  `0.2.56-SNAPSHOT`.

### Notes

- No normative specification change is introduced.
- No new Core family or standard prototype is materialized by I002. In
  particular, the existing String runtime representation is left unchanged until
  its standard Core bootstrap/protocol work is implemented; future specialized
  families can adopt the same bridge without adding dispatcher cases.


## [0.2.55-SNAPSHOT] - 2026-09-04

### Added

- Completed the I004 standard Array conformance audit against the current
  normative collection, object-model, callable, and Error contracts.
- Added exhaustive Array completion coverage for fresh/empty construction,
  dense boundary indexing, exact indexed-mutation result, closed/frozen
  mutation behavior, semantic Integer size, polymorphic non-Closure callback
  invocation, shallow snapshot order, callback failure propagation, ordinary
  identity/default equality, and receiver-domain rejection.
- Added explicit coverage that an ordinary object delegating to `Array` does
  not acquire standard Array indexed state, while an inherited Array factory
  still creates a real standard Array whose parent is the invocation receiver.
- Project implementation version changed from `0.2.54-SNAPSHOT` to `0.2.55-SNAPSHOT`.

### Notes

- The audit found the existing standard Array runtime behavior already aligned
  with the current normative surface; this completion closes the remaining
  conformance gaps without changing Array runtime semantics.
- No Array literal, growth, slicing, insertion/removal, negative-from-end
  indexing, or unrelated collection behavior is introduced.
- I001 (`args`), I002 (represented-value lookup), I007 (Error infrastructure),
  and the normative specification are intentionally untouched.



## [0.2.54-SNAPSHOT] - 2026-09-04

### Fixed

- I001: `args` now lowers uniformly to `ProtosArgsNode` instead of retaining a
  generic-lowering `UnsupportedOperationException` path for nested canonical
  expression shapes.
- Object-construction activations preserve the enclosing invocation's already
  materialized `args` Array while retaining existing construction `context`,
  receiver, lexical-capture, method-home, and return-home semantics.
- Added end-to-end coverage for zero/one/multiple arguments, ordering, mixed
  value families, element identity, fresh Array identity, nested Closure and
  method calls, polymorphic invocation, non-local return, and object bodies.
- Replaced the obsolete regression that explicitly expected `args` lowering to
  remain unimplemented.
- Project implementation version changed from `0.2.53-SNAPSHOT` to
  `0.2.54-SNAPSHOT`.

### Notes

- `args` remains the fresh frozen standard Array established from the flattened
  caller-supplied positional vector; receiver/default values are not inserted.
- No normative specification change is introduced.


## [0.2.53-SNAPSHOT] - 2026-09-04

### Fixed

- Updated `CanonicalizerEqualityTest` to the normative P66 lowering:
  source `!=` canonicalizes to an ordinary `!=` message send, and source `!==`
  canonicalizes to the dedicated non-dispatchable `CanonicalNotIdentity` form.
- Removed the obsolete test expectations that both operators lowered through
  the unrelated `not` selector.
- Project implementation version changed from `0.2.52-SNAPSHOT` to
  `0.2.53-SNAPSHOT`.

### Notes

- No runtime semantics changed.
- No normative specification change is introduced.


## [0.2.52-SNAPSHOT] - 2026-09-04

### Added

- Implemented standard `Object.==` using Protos semantic identity as the default
  equality for receivers without a nearer equality override.
- Implemented standard `Object.!=` as the strict Boolean complement of the
  receiver's dynamically selected current `==` behavior.
- `Object.!=` propagates equality errors and rejects a non-Boolean normal result
  instead of applying truthiness.
- Added a dedicated canonical non-identity form and Truffle execution node so
  `!==` is the primitive Boolean complement of `===` with no `==`, `!=`, or
  `not` message dispatch.
- Corrected source `!=` lowering to send the ordinary `!=` selector rather than
  synthesizing a `not` send after `==`.
- Added Java and executable `.protos` conformance coverage for default object
  equality, numeric inequality, fixed-width cross-family inequality, and
  primitive non-identity.
- Project implementation version changed from `0.2.51-SNAPSHOT` to
  `0.2.52-SNAPSHOT`.

### Notes

- Prefix `!` remains a separate implementation/spec audit item; this patch does
  not invent standard `not` behavior.
- No normative specification change is introduced.


## [0.2.51-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the standard numeric-equality wrong-arity regression test so it
  invokes the `==` selector directly with two supplied arguments through
  `ProtosInvocation`.
- Removed the invalid source spelling `1.==(1, 2)`, which is rejected by the
  parser before message dispatch and therefore could not test runtime arity
  behavior.
- Project implementation version changed from `0.2.50-SNAPSHOT` to
  `0.2.51-SNAPSHOT`.

### Notes

- No numeric equality runtime semantics changed.
- No normative specification change is introduced.


## [0.2.50-SNAPSHOT] - 2026-09-04

### Added

- Implemented standard Number-family `==` as an ordinary `Number`-owned
  Closure-valued slot inherited by Integer, Float, and all fixed-width integer
  prototypes.
- Numeric equality compares mathematical numeric value across semantic numeric
  families without performing arithmetic coercion or narrowing.
- Added exact Float-vs-exact-integer comparison using the actual represented
  binary64 value, avoiding rounded host-integer comparison.
- Implemented IEEE-style NaN equality (`NaN == x` is always false), signed-zero
  numeric equality, infinity equality, and cross-family exact-integer equality.
- Standard Number equality with a non-Number argument returns canonical `false`.
- Added receiver-domain protection so ordinary objects that merely inherit
  Number-family `==` are not treated as semantic Numbers.
- Added Java and executable `.protos` conformance coverage for cross-family,
  exact-rounding, NaN, signed-zero, infinity, and non-Number cases.
- Project implementation version changed from `0.2.49-SNAPSHOT` to
  `0.2.50-SNAPSHOT`.

### Notes

- `!=` is intentionally not implemented in this slice. The current canonicalizer
  lowers it through a `not` send, but Core's normative Boolean section does not
  currently define a standard `not` selector. That normative gap is left
  untouched rather than inventing behavior.
- No normative specification change is introduced.


## [0.2.49-SNAPSHOT] - 2026-09-04

### Added

- Implemented the standard Boolean protocol selectors `ifTrue`, `ifFalse`,
  `and`, and `or` as ordinary Object-owned Closure-valued slots reached by the
  canonical `true`/`false` delegation bridge.
- Added exact Boolean receiver-domain enforcement: standard Boolean behavior
  accepts only canonical `true` and `false`.
- Implemented selected-path ordinary polymorphic callback invocation with zero
  positional arguments and exact propagation of normal results for `ifTrue`
  and `ifFalse`.
- Implemented path-local callability validation: callbacks on unselected paths
  are neither validated nor invoked.
- Implemented canonical Boolean result validation for selected `and` and `or`
  callbacks, with invalid normal results signaling Error.
- Added `boolean` and `null` language-conformance expectation kinds and
  executable `.protos` cases for selected/unselected paths, short-circuiting,
  invalid callback results, and non-invokable selected callbacks.
- Project implementation version changed from `0.2.48-SNAPSHOT` to
  `0.2.49-SNAPSHOT`.

### Notes

- No standard `Boolean` prototype or prelude binding is introduced.
- The already-published direct canonical Boolean delegation to `Object` remains
  unchanged.
- No truthiness conversion, implicit awaiting, hidden suspension, or callback
  pre-validation is introduced.
- No normative specification change is introduced.


## [0.2.48-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard prototypes `UInt8`, `Int8`, `UInt16`, `Int16`,
  `UInt32`, `Int32`, `UInt64`, and `Int64`, each delegating directly through
  `Integer`.
- Added semantic fixed-width integer runtime values carrying exact family and
  mathematical value with range-enforced construction.
- Extended ordinary lookup to each fixed-width numeric prototype.
- Added all eight explicit fixed-width conversion factories with exact range
  checks for Integer, integral Float, and cross-family fixed-width inputs.
- Extended `Integer(...)` and `Float(...)` to accept fixed-width exact integers.
- Added Java and `.protos` conformance coverage and a `fixed-integer`
  expectation kind.
- Project implementation version changed from `0.2.47-SNAPSHOT` to
  `0.2.47-SNAPSHOT`.

### Notes

- Fixed-width arithmetic is intentionally deferred to P64.
- No implicit numeric promotion or wrapping is introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.47-SNAPSHOT] - 2026-09-04

### Added

- Implemented the canonical Boolean delegation bridge: represented `true` and
  `false` now continue ordinary lookup directly through `Object`.
- Added regression coverage for inherited Object lookup, original-receiver
  dispatch through polymorphic invocation, and absence of a standard `Boolean`
  prelude prototype.
- Closed implementation blocker B003 after D027 normatively fixed the Boolean
  parent topology.
- Project implementation version changed from `0.2.46-SNAPSHOT` to
  `0.2.47-SNAPSHOT`.

### Notes

- Canonical Booleans remain the existing host singleton representations
  `ProtosBooleanValue.TRUE` and `ProtosBooleanValue.FALSE`.
- No standard `Boolean`, `Value`, or other synthetic Protos-visible ancestor is
  introduced.
- No normative specification document or specification revision changed.

## [0.2.46-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P62 non-integral Float-to-Integer rejection test to use `1.5`,
  a binary64 value that is actually finite and mathematically non-integral.
- Removed the incorrect test assumption that the source literal
  `9007199254740991.5` remains non-integral after Float literal rounding; that
  decimal source rounds to the exact binary64 value `9007199254740992.0`,
  which is mathematically integral and therefore valid input to `Integer(...)`.
- Project implementation version changed from `0.2.45-SNAPSHOT` to
  `0.2.46-SNAPSHOT`.

### Notes

- No runtime conversion semantics changed.
- Existing P62 implementation remains unchanged.
- No normative specification change is introduced.
- D027/B003 remains untouched.


## [0.2.45-SNAPSHOT] - 2026-09-04

### Added

- Added standard one-argument ordinary invocation factories for the `Integer`
  and `Float` prototype objects.
- `Integer(value)` now accepts ordinary Integer values and finite mathematically
  integral Float values, returning the exact unbounded Integer without rounding
  or truncation.
- Float-to-Integer conversion derives the exact mathematical integer represented
  by binary64 bits rather than relying on decimal rendering or host narrowing.
- `Float(value)` now preserves existing Float semantic values and converts exact
  Integers with exact-to-binary64 `roundTiesToEven`, including precision loss and
  overflow to infinity required by Core.
- Added arity, non-Number, non-integral Float, NaN, infinity, and incompatible
  invocation-receiver rejection.
- Added receiver-domain protection so inheriting or copying a standard numeric
  factory `call` does not turn an ordinary object into a numeric conversion
  prototype.
- Added Java and executable `.protos` conformance coverage, including the
  adversarial exact value of `Integer(1e23)`.
- Project implementation version changed from `0.2.44-SNAPSHOT` to
  `0.2.45-SNAPSHOT`.

### Notes

- This slice implements the currently represented ordinary `Integer` and
  `Float` semantic families. Fixed-width conversion factories remain separate
  until those eight semantic families have runtime representations.
- No implicit numeric promotion or coercion is introduced by these explicit
  factories.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.44-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary Float arithmetic for `+`, `-`, `*`, `/`, and unary
  `negated`.
- Standard Float arithmetic operates directly on semantic binary64 operands and
  produces binary64 results, including signed zero, subnormal/underflow,
  infinity, and NaN behavior required by IEEE 754-2019.
- Float division by zero and invalid IEEE arithmetic now produce the
  corresponding Float infinity or NaN rather than a Protos Error.
- Added Float receiver-domain validation and rejection of mixed Float/Integer
  arithmetic without implicit numeric promotion or coercion.
- Added Java coverage for normal arithmetic, signed-zero negation, division by
  zero, overflow, underflow, NaN-producing operations, mixed-family rejection,
  and copied-method incompatible receivers.
- Added executable `.protos` Float conformance programs and a semantic
  `float-nan` expectation that does not expose implementation-specific NaN
  payload or sign bits.
- Project implementation version changed from `0.2.43-SNAPSHOT` to
  `0.2.44-SNAPSHOT`.

### Notes

- Java `double` is used here only as the host representation of Protos binary64
  primitive operations; no wider intermediate value is retained across a Protos
  operation boundary.
- NaN conformance intentionally tests semantic NaN membership rather than raw
  NaN payload bits, which are not portable Protos surface semantics.
- Numeric comparison/equality/hash and explicit numeric conversion factories
  remain separate implementation slices.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.43-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer / Integer` behavior returning the correctly
  rounded IEEE binary64 Float representation of the exact mathematical rational
  quotient.
- Added exact integer-arithmetic binary64 rounding with `roundTiesToEven`,
  including normal values, subnormals, signed underflow zero, overflow to
  infinity, and the normal/subnormal boundary.
- Integer division now rounds the exact rational quotient once rather than first
  converting each arbitrary-precision Integer operand to a host `double`.
- Added Java coverage for huge operands, halfway ties, subnormal rounding,
  signed zero, infinity, zero-divisor failure, and mixed-family rejection.
- Extended the language conformance manifest with `float-bits` expectations so
  Float results can be checked by exact raw binary64 representation.
- Added executable `.protos` conformance programs for Integer division,
  including adversarial cases where separate host-double operand conversion
  would produce the wrong result.
- Project implementation version changed from `0.2.42-SNAPSHOT` to
  `0.2.43-SNAPSHOT`.

### Notes

- This slice implements only ordinary `Integer / Integer`. Standard Float
  arithmetic and fixed-width integer-family division remain separate work.
- Exact zero divided by a nonzero Integer produces positive `0.0`; a nonzero
  exact quotient that rounds to zero preserves the quotient sign as required.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.42-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer.div(argument)` quotient behavior with
  truncation toward zero.
- Added standard ordinary `Integer.mod(argument)` and `%` remainder behavior
  using `a - (a div b) * b`, preserving the dividend sign for nonzero
  remainders.
- Added zero-divisor and mixed-numeric-family rejection for standard Integer
  quotient and remainder operations.
- Added exact arbitrary-precision quotient/remainder coverage in Java and
  executable `.protos` conformance programs.
- Added conformance cases for positive and negative operands, zero divisors,
  mixed Integer/Float rejection, and `%` equivalence with standard `mod`.
- Project implementation version changed from `0.2.41-SNAPSHOT` to
  `0.2.42-SNAPSHOT`.

### Notes

- Ordinary Integer `/` remains separate work because Core requires its Float
  result to be the correctly rounded binary64 representation of the exact
  rational quotient; it must not be implemented by separately rounding large
  Integer operands to host doubles before division.
- Float `div`, `mod`, and `%` are intentionally not introduced.
- Fixed-width integer-family quotient/remainder behavior remains separate until
  those semantic families are represented by the implementation.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.41-SNAPSHOT] - 2026-09-04

### Added

- Added the first end-to-end Protos language conformance harness.
- Added executable `.protos` conformance programs under
  `protos/tests/conformance/`, keeping the language test corpus independent from
  the Java/Maven resource layout.
- Added an external tab-separated expectation manifest so conformance assertions
  remain outside the Protos language itself.
- Added P57 Integer conformance programs covering small arithmetic, negative
  results, arbitrary-precision overflow boundaries, unary negation, and
  mixed-family Error cases.
- Conformance programs execute through `ProtosSourceFileLoader`, the normal
  source compiler/lowering/runtime path, and a freshly bootstrapped Core prelude.
- Project implementation version changed from `0.2.40-SNAPSHOT` to
  `0.2.41-SNAPSHOT`.

### Notes

- The JUnit runner remains under `src/test/java`, but the Protos conformance
  corpus is intentionally implementation-layout-independent under `protos/tests`.
- Existing Java unit/integration tests remain valuable for implementation
  invariants; Protos conformance tests complement rather than replace them.
- No test-only Protos syntax, assertion primitive, privileged test object, or
  standard-library testing API is introduced.
- Future observable language slices should add `.protos` conformance programs
  when their behavior can be expressed through the executable language surface.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.40-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer` arithmetic behavior for `+`, `-`, `*`, and
  unary `negated`.
- Ordinary Integer arithmetic now returns exact unbounded Integer values backed
  by arbitrary-precision arithmetic and therefore does not expose host-machine
  integer overflow.
- Standard Integer binary arithmetic rejects arguments from other numeric
  families instead of implicitly promoting or coercing them.
- Added receiver-domain validation so copying a standard Integer arithmetic
  Closure onto an ordinary object does not make that receiver a semantic
  Integer.
- Added coverage for large exact results, unary negation, mixed-family rejection,
  and incompatible receivers.
- Project implementation version changed from `0.2.39-SNAPSHOT` to `0.2.40-SNAPSHOT`.

### Notes

- Division, remainder/modulo, comparisons, conversion factories, Float
  arithmetic, and fixed-width integer families remain separate implementation
  work.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.39-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected P56 `Array.each` tests so they validate callback order and shallow
  snapshot behavior without depending on the not-yet-implemented standard
  numeric `+` operator.
- The revised tests use ordinary invokable native-backed Closure values only as
  test callbacks, preserving the same `Array.each` invocation path while
  isolating the behavior under test.
- Project implementation version changed from `0.2.38-SNAPSHOT` to `0.2.39-SNAPSHOT`.

### Notes

- No `Array.each` runtime semantics are changed.
- No numeric arithmetic behavior is introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.38-SNAPSHOT] - 2026-09-04

### Added

- Added standard `Array.size()` as a read-only ordinary Closure-valued protocol
  operation returning the semantic Integer indexed length.
- Added standard `Array.each(block)` with ordinary polymorphic callback
  validation, ascending shallow-snapshot traversal, one exact element argument
  per callback, and original-receiver normal result.
- Array iteration snapshots are isolated from later element replacement while
  preserving ordinary element identity and callback effects.
- Added coverage for open/closed/frozen size observation, iteration order,
  snapshot replacement behavior, callback validation, and exact receiver result.
- Project implementation version changed from `0.2.37-SNAPSHOT` to `0.2.38-SNAPSHOT`.

### Notes

- `each` invokes callbacks through the existing ordinary invocation protocol;
  it does not require callbacks to be Closures.
- No parallel Array operations are introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.37-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Array.at(index)` and `Array.atPut(index, value)`
  Closure-valued protocol slots on the source-backed `Array` object.
- Standard Array indexed reads now require a semantic Integer index in the dense
  range `0 <= index < length` and return the exact stored element.
- Standard Array indexed updates replace exactly one existing element, preserve
  length, return the exact supplied value, allow replacement on closed Arrays,
  and reject mutation of frozen Arrays before index validation.
- Added executable lowering for `CanonicalIndexedAssign`, preserving the
  syntax-level evaluation order receiver -> index -> RHS -> `atPut` and returning
  the exact RHS after normal `atPut` completion.
- Added coverage for custom `atPut` return values, closed/frozen Arrays,
  non-Integer indices, and bounds failures.
- Project implementation version changed from `0.2.36-SNAPSHOT` to `0.2.37-SNAPSHOT`.

### Notes

- Bracket syntax remains ordinary `at` / `atPut` protocol dispatch rather than a
  privileged Array runtime operation.
- No insertion, growth, holes, negative-from-end indexing, or Array literals are
  introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.36-SNAPSHOT] - 2026-09-04

### Added

- Added the standard source-backed `Array` object's ordinary local `call`
  specialization as an Array factory.
- `Array(...)` now creates a fresh open `ProtosArrayValue` containing the exact
  supplied positional objects in order, with no cloning, freezing, or
  Integer-length overload.
- Inherited Array-factory invocation uses the original invocation receiver as
  the new Array's delegation parent, so ordinary descendants such as `MyArray`
  construct Arrays delegating to that descendant.
- Added receiver-domain validation so copying the standard Array factory Closure
  onto an unrelated object does not make that object a standard Array-family
  factory.
- Project implementation version changed from `0.2.35-SNAPSHOT` to `0.2.36-SNAPSHOT`.

### Notes

- The factory is installed as an ordinary Closure-valued `call` slot on the
  source-loaded `Array` object; no hidden invocation special case is added.
- Indexed Array protocol methods remain separate implementation work.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.35-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P53 message-send spread test so it exercises spread flattening
  with an already-materialized `ProtosArrayValue` instead of depending on the
  still-pending standard `Array.call` factory specialization.
- Project implementation version changed from `0.2.34-SNAPSHOT` to `0.2.35-SNAPSHOT`.

### Notes

- No message-send runtime semantics are changed.
- No standard Array factory behavior is introduced by this correction.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.34-SNAPSHOT] - 2026-09-04

### Added

- Added executable lowering for `CanonicalSend` through a dedicated
  `ProtosSendNode`.
- Added direct ordinary message invocation for arbitrary Protos receivers using
  the existing semantic value lookup bridge, preserving original receiver and
  physical `methodHome`.
- Added coverage for local and inherited method sends, argument/spread handling,
  and missing-message Core Error behavior.
- Project implementation version changed from `0.2.33-SNAPSHOT` to `0.2.34-SNAPSHOT`.

### Changed

- Callable lowering now recursively supports nested `CanonicalSend` expressions
  in closure bodies and defaults.

### Notes

- Message send remains ordinary slot lookup plus Closure activation; no parallel
  host dispatch mechanism is introduced.
- D027/B003 remains untouched: this change does not choose a parent for
  canonical `true` or `false` and does not introduce a `Boolean` prototype.
- No normative specification change is introduced.


## [0.2.33-SNAPSHOT] - 2026-09-04

### Fixed

- Allowed semantic member lookup to traverse ordinary-object delegation chains
  without requiring a Core prelude when no represented value boundary is
  crossed.
- Required the Core prelude lazily only when the semantic lookup walker actually
  reaches represented Integer or Float values.
- Restored extracted-Closure member-read execution in minimal activations while
  preserving represented numeric lookup through source-backed Core prototypes.
- Project implementation version changed from `0.2.32-SNAPSHOT` to `0.2.33-SNAPSHOT`.

### Notes

- D027/B003 remains untouched. No parent for canonical `true` or `false` is
  selected and no `Boolean` prototype is introduced.
- No normative specification change is introduced.


## [0.2.32-SNAPSHOT] - 2026-09-04

### Fixed

- Routed member lookup through the semantic value walker even when the initial
  receiver is an ordinary object, so an ordinary delegation chain can continue
  through a represented numeric value and then into its source-backed
  `Integer`/`Float`/`Number` prototype chain.
- Prevented the legacy `ProtosObjectValue.lookupSlot` host exception from
  escaping that mixed ordinary/represented delegation path.
- Preserved the existing language-level Core Error behavior for represented
  value families whose prototype bridge is not implemented in this slice.
- Project implementation version changed from `0.2.31-SNAPSHOT` to `0.2.32-SNAPSHOT`.

### Notes

- No delegation parent is selected for canonical `true` or `false`; D027/B003
  remains untouched.
- No `Boolean` prototype or Boolean fallback is introduced.
- No normative specification change is introduced.


## [0.2.31-SNAPSHOT] - 2026-09-04

### Fixed

- Restored minimal/internal `ProtosPrelude` construction without requiring
  source-backed `Number`, `Integer`, and `Float` bindings in every prelude.
  Full Core bootstrap remains responsible for loading and validating the numeric
  hierarchy.
- Ordinary `ProtosObjectValue` member reads no longer require an owning Core
  prelude. A prelude is required only when lookup crosses from a represented
  non-ordinary runtime value into its source-backed standard prototype chain.
- Corrected P52 numeric receiver-binding tests to use member extraction followed
  by the already-supported ordinary `CanonicalCall` path instead of unsupported
  `CanonicalSend` lowering.
- Project implementation version changed from `0.2.30-SNAPSHOT` to `0.2.31-SNAPSHOT`.

### Notes

- Numeric hierarchy semantics from P52 are unchanged.
- D027/B003 remains untouched: no parent is chosen for canonical `true` or
  `false`, and no `Boolean` prototype is introduced.
- No normative specification change is introduced.


## [0.2.30-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard `Number`, `Integer`, and `Float` prototype objects
  under `protos/lib/core/`.
- Added the runtime value-lookup bridge that maps semantic Integer and Float
  value representations into those source-backed ordinary prototype chains.
- Added coverage for numeric prototype hierarchy, inherited numeric lookup,
  exact receiver preservation, and ordinary objects delegating to numeric
  values.
- Added implementation blocker B003 so canonical Boolean parentage remains
  explicitly deferred to D027 rather than being guessed by the runtime.

### Changed

- Core bootstrap now loads and validates `Number -> Object`,
  `Integer -> Number`, and `Float -> Number`, then publishes those exact objects
  in the frozen prelude.
- Member lookup, invocation lookup, and activation receiver fallback now use the
  semantic value lookup bridge when a Core prelude is available.
- Project implementation version changed from `0.2.29-SNAPSHOT` to `0.2.30-SNAPSHOT`.

### Notes

- This change intentionally does not choose a parent for canonical `true` or
  `false`, does not create a `Boolean` prototype, and does not install Boolean
  lookup behavior. That work remains blocked on D027.
- String/null and the remaining standard value families are not assigned
  substitute parentage by this increment.
- No normative specification change is introduced.


## [0.2.29-SNAPSHOT] - 2026-09-04

### Added

- Added ordinary parenthesized invocation through the normative `call` slot protocol.
- Added `CanonicalCall` lowering, target-before-argument evaluation, spread flattening, ordinary `call` lookup, Closure validation, receiver/method-home binding, and terminal direct Closure activation.
- Installed standard `Object.call` and `Object.init` as ordinary Closure-valued slots on `Object`, with default construction and D022's `Object.init() -> this` result.

### Changed

- `ProtosClosureValue` now participates in the ordinary object/delegation model as a direct child of `Object`; standard Closure invocation therefore inherits `Object.call` rather than using a hidden callable flag.
- Extracted/bound Closure wrappers preserve ordinary local slots and structural state while replacing only receiver/method-home binding metadata.
- Project implementation version changed from `0.2.28-SNAPSHOT` to `0.2.29-SNAPSHOT`.

### Tests

- Added end-to-end coverage for plain Closure calls, local and inherited `call`, incompatible shadowing, default construction, overridden and standard `init`, spread, nested calls, and non-local return across ordinary calls.

### Notes

- Host-represented primitive value prototype bridging and standard Array/Map/numeric `call` specializations remain later implementation layers.
- No normative specification change is introduced.


## [0.2.28-SNAPSHOT] - 2026-09-04

### Changed

- Closed implementation blocker B002 for the runtime semantics of
  `Object.without(name)` and `Object.alias(sourceName, aliasName)` structural
  views.
- Structural-view results now always use the unique root `Object` as their
  immediate delegation parent rather than accepting an implementation-selected
  parent.
- The result remains a fresh open ordinary object regardless of the source
  object's parent or open/closed/frozen state.
- Project implementation version changed from `0.2.27-SNAPSHOT` to `0.2.28-SNAPSHOT`.

### Tests

- Extended object-runtime coverage for fresh identity, root-`Object` parent,
  open result state, frozen-source behavior, shallow exact-value copying,
  mutation independence, and delegated alias-name non-collision.

### Notes

- This block implements the now-closed runtime object semantics without exposing
  new Protos-visible messages yet; ordinary message dispatch remains pending the
  invocation-protocol work.
- `CanonicalCall` remains deliberately unopened while the current specification
  does not yet define the portable inheritance/replacement mechanism of the
  ordinary invocation protocol.
- No normative specification change is introduced.


## [0.2.27-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard `InvalidReturn` under `protos/lib/core/`, with
  direct delegation to the standard `Error` prototype.
- Added `ProtosReturnNode` and a dedicated internal non-local-return control
  transfer carrying the exact target home and result value.
- Callable-plan lowering now lowers canonical `^value` in Closure defaults and
  bodies.
- Added fresh `InvalidReturn` construction through the activation-owned Core
  prelude.

### Changed

- Selected Closure invocation now catches a non-local return only when the
  invocation owns the exact target home; nested invocations sharing a captured
  home rethrow the transfer unchanged.
- An owned home is still completed on every exit path, including a handled
  non-local return.
- Project implementation version changed from `0.2.26-SNAPSHOT` to `0.2.27-SNAPSHOT`.

### Tests

- Added integrated coverage for direct active `^`, `^` from a default
  expression, nested captured-home propagation, escaped-Closure
  `InvalidReturn`, fresh Error identity, and the exact source-backed
  `InvalidReturn -> Error` prototype relationship.
- Extended Core bootstrap coverage for the standard `InvalidReturn` prototype.

### Notes

- This block closes non-local return execution for already selected Closures.
- Ordinary `CanonicalCall`, message send, `super`, and polymorphic object
  invocation remain outside this block.
- No normative specification change is introduced.


## [0.2.26-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosClosureInvoker` for executing an already selected Closure through
  activation establishment, normative parameter binding, and body execution.
- Closure execution plans now own reusable Truffle call targets for binding and
  body execution.

### Changed

- An invocation-owned return home is completed when that invocation leaves its
  dynamic binding/body extent, including failure exits.
- Nested Closure invocations that reuse a captured return home never complete
  that home themselves.
- Project implementation version changed from `0.2.25-SNAPSHOT` to `0.2.26-SNAPSHOT`.

### Tests

- Added coverage for selected-Closure invocation, normal owned-home completion,
  binding failure propagation, and preservation of a captured nested return home.

### Notes

- This increment still does not lower or dispatch `CanonicalCall`/message sends.
- Non-local `^` transfer itself remains unopened; this slice establishes the
  lifecycle boundary it will target.
- No normative specification change is introduced.


## [0.2.25-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P47 callable-lowering test to import `ProtosParser` from its
  actual `com.guillermomolina.protos.parser` package.

### Changed

- Project implementation version changed from `0.2.24-SNAPSHOT` to `0.2.25-SNAPSHOT`.

### Notes

- No production implementation or Protos semantics changed in this corrective
  commit.



## [0.2.24-SNAPSHOT] - 2026-09-04

### Added

- Closure lowering now prepares an implementation-private execution plan
  containing the parameter-binding node and body node without invoking either.
- Callable-plan lowering recognizes `args` in Closure defaults and bodies and
  lowers it to `ProtosArgsNode`.
- Materialized Closure values retain their prepared execution plan, including
  across extracted-method binding.

### Changed

- General program/module lowering continues to reject `args`; only callable
  plans receive invocation-context `args` lowering.
- Project implementation version changed from `0.2.23-SNAPSHOT` to `0.2.24-SNAPSHOT`.

### Tests

- Added coverage proving both a default expression and a Closure body plan can
  observe the exact activation-owned `args` Array.

### Notes

- This increment does not execute calls, parameter binding automatically, or
  Closure bodies automatically; it only prepares the complete callable plan.
- Return-home completion and `^` execution remain unopened.
- No normative specification change is introduced.


## [0.2.23-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosArgsNode`, an invocation-only execution node that returns the
  exact standard frozen Array already established on the current Closure
  activation.

### Changed

- Project implementation version changed from `0.2.22-SNAPSHOT` to `0.2.23-SNAPSHOT`.

### Tests

- Added focused coverage proving `args` returns the exact activation-owned Array,
  retaining its source-backed standard `Array` parent and frozen state.

### Notes

- This increment intentionally does not enable `args` in the general
  program/module lowerer because Core defines it as an invocation-context
  binding and does not assign a substitute value outside invocation.
- Callable-body/default lowering will use this node when the Closure execution
  plan is connected.
- No normative specification change is introduced.


## [0.2.22-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosParameterBindingNode`, implementing the normative left-to-right
  Closure parameter-binding algorithm over an already established invocation
  activation.
- Binding supports supplied arguments, real-activation default evaluation,
  trailing rest capture as a distinct fresh frozen standard Array, and generic
  argument-count Error signaling.

### Changed

- Project implementation version changed from `0.2.21-SNAPSHOT` to `0.2.22-SNAPSHOT`.

### Tests

- Added coverage for earlier-parameter visibility from defaults, exact `args`
  preservation, distinct frozen rest Arrays, missing required parameters, and
  deferred excess-argument detection.

### Notes

- Parameter names are created only after their supplied/default value is
  obtained; there is no predeclaration or arity preflight.
- Slot-creation conflicts during parameter establishment use ordinary generic
  Error signaling.
- This increment does not yet execute `CanonicalCall`, lower the `args`
  intrinsic, execute Closure bodies, or complete return homes.
- No normative specification change is introduced.


## [0.2.21-SNAPSHOT] - 2026-09-04

### Added

- Closures now preserve their implementation-private owning `ProtosPrelude`
  together with lexical contexts and callable control metadata.
- Added `ProtosActivation.forClosureInvocation(...)`, which atomically
  establishes a fresh execution context, exact captured receiver and lexical
  contexts, source-backed frozen `args`, `methodHome`, and return-home state.
- Invocation activations record whether they own a newly established return home
  or reuse a captured lexical home.

### Changed

- Closure literal materialization now captures the exact owning prelude.
- Project implementation version changed from `0.2.20-SNAPSHOT` to `0.2.21-SNAPSHOT`.

### Tests

- Added coverage for complete top-level Closure activation establishment and for
  nested Closure reuse of the exact captured return home.

### Notes

- This increment still does not execute `CanonicalCall`, bind parameters or
  defaults/rest, execute Closure bodies, complete return homes, or execute `^`.
- No partial observable call path is introduced.
- No normative specification change is introduced.


## [0.2.20-SNAPSHOT] - 2026-09-04

### Added

- Added implementation-private activation `methodHome` state matching the
  normative `super` lookup model.
- Closure literals now capture the current activation's `methodHome` together
  with receiver and return-home metadata.

### Changed

- Object-construction execution preserves enclosing `methodHome` metadata while
  continuing not to become a lexical capture scope.
- Project implementation version changed from `0.2.19-SNAPSHOT` to `0.2.20-SNAPSHOT`.

### Tests

- Added focused coverage proving construction preserves the exact enclosing
  method home.

### Notes

- This increment does not execute `super`, perform callable dispatch, or create
  method activations yet.
- No normative specification change is introduced.


## [0.2.19-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosReturnHome`, an implementation-private identity object with
  explicit active/completed lifecycle state for callable non-local return
  ownership.
- `ProtosActivation` can now carry an optional lexical return home, and object
  construction preserves the enclosing home.
- `ProtosClosureValue` now captures and preserves the exact lexical return home,
  including across extracted-method binding.

### Changed

- Closure literal materialization now records the current activation's return
  home when one exists.
- Project implementation version changed from `0.2.18-SNAPSHOT` to
  `0.2.19-SNAPSHOT`.

### Tests

- Added focused return-home lifecycle and object-construction propagation
  coverage.

### Notes

- This increment does not yet execute `^`, create callable activations, or
  establish fresh invocation return homes.
- It does not add activation-level `methodHome`; `super` activation state remains
  a separate subsequent slice.
- No normative specification change is introduced.


## [0.2.18-SNAPSHOT] - 2026-09-04

### Added

- Added implementation-private invocation-argument state to `ProtosActivation`.
  Non-invocation activations expose no argument Array.
- Added `ProtosPrelude.newFrozenArray(...)`, producing a fresh frozen standard
  Array with the exact source-backed `Array` prototype.

### Changed

- Test preludes now include the mandatory standard `Array` binding introduced
  by the source-backed Core bootstrap.
- Project implementation version changed from `0.2.17-SNAPSHOT` to
  `0.2.18-SNAPSHOT`.

### Tests

- Extended Core bootstrap coverage for fresh frozen standard Array
  materialization.

### Notes

- This increment prepares the activation representation required by the
  normative `args` semantics but does not yet establish callable activations,
  bind parameters, or lower the `args` intrinsic.
- No observable invocation shortcut or partial call execution is introduced.
- No normative specification change is introduced.


## [0.2.17-SNAPSHOT] - 2026-09-04

### Added

- Added distributable `protos/lib/core/array.protos`, defining the standard
  `Array` prototype as an ordinary child of `Object`.
- Added `ProtosPrelude.arrayPrototype()` and `newArray(...)` so runtime
  machinery can materialize standard Arrays with the exact source-backed
  Array prototype as delegation parent.

### Changed

- Core bootstrap now loads and validates the `Array` binding before freezing
  the standard prelude and installs that exact source-created object alongside
  `Context` and `Error`.
- Project implementation version changed from `0.2.16-SNAPSHOT` to
  `0.2.17-SNAPSHOT`.

### Tests

- Extended Core bootstrap coverage to verify the exact source-backed Array
  binding, its delegation parent, and the parent of a materialized standard
  Array value.

### Notes

- This increment does not implement Array invocation, `at`, `atPut`, `size`,
  `each`, `args`, rest binding, or any other Array protocol.
- No hardcoded Java Array prototype singleton is introduced.
- No normative specification change is introduced.


## [0.2.16-SNAPSHOT] - 2026-09-04

### Added

- Added distributable `protos/lib/core/error.protos`, defining the mandatory
  standard `Error` prototype as an ordinary child of `Object`.

### Changed

- Core bootstrap now executes both `context.protos` and `error.protos`, validates
  their exact required parent relationships, installs both bindings into the
  frozen standard prelude, and exposes `Error` through that prelude.
- Runtime language failures now create fresh error objects whose parent is the
  exact source-backed `Error` prototype owned by the current activation's
  prelude.
- `ProtosCoreErrors` is now a stateless runtime factory; its process-global
  `ERROR_PROTOTYPE` singleton and `errorPrototype()` accessor were removed.
- Project implementation version changed from `0.2.15-SNAPSHOT` to
  `0.2.16-SNAPSHOT`.

### Tests

- Runtime error tests now use an explicit test prelude rather than the removed
  production Error singleton.
- Core bootstrap coverage verifies the source-backed Error binding and its
  direct delegation to Object.

### Notes

- A module-local binding named `Error` cannot redirect runtime-generated Core
  errors because runtime failure identity is obtained from the activation's
  owning prelude, not ordinary shadowable lexical lookup.
- No second Error identity or process-global standard Error object remains.
- No normative specification change is introduced.


## [0.2.15-SNAPSHOT] - 2026-09-04

### Changed

- Activations created by a `ProtosPrelude` now retain an implementation-private
  reference to that exact owning prelude.
- Object-construction activations preserve the owning prelude from their
  enclosing activation.
- Legacy direct activation construction remains available for bootstrap and
  focused runtime tests and carries no implicit process-global prelude.
- Project implementation version changed from `0.2.14-SNAPSHOT` to
  `0.2.15-SNAPSHOT`.

### Tests

- Added coverage that module activations retain their exact prelude and that
  object-construction activations propagate it unchanged.

### Notes

- This is runtime plumbing only and introduces no Protos-visible binding,
  lookup rule, identity, or behavior change.
- The explicit prelude reference is the foundation for resolving standard Core
  identities such as `Error` without using shadowable lexical lookup or
  process-global standard-object singletons.
- No normative specification change is introduced.


## [0.2.14-SNAPSHOT] - 2026-09-04

### Changed

- `ProtosPrelude` now owns an explicit frozen ordinary Protos bindings context
  rather than only retaining the `Context` prototype reference.
- `ProtosCoreBootstrap` now constructs that real prelude context after loading
  `Context`: it delegates to `Context`, contains the exact `Context` binding,
  and is frozen before becoming observable to later runtime stages.
- Added `ProtosPrelude.newModuleActivation()` so a fresh module context captures
  the frozen standard prelude through the ordinary lexical-context mechanism.
- Project implementation version changed from `0.2.13-SNAPSHOT` to
  `0.2.14-SNAPSHOT`.

### Tests

- Added coverage for frozen prelude structure and ordinary lexical lookup of the
  `Context` binding from a module activation.
- Extended Core bootstrap coverage to verify the source-backed prelude context.

### Notes

- This does not add any new standard objects or hardcoded Core behavior.
- The prelude remains explicit runtime state; no process-global mutable prelude
  is introduced.
- No normative specification change is introduced.


## [0.2.13-SNAPSHOT] - 2026-09-04

### Removed

- Removed the temporary static `ProtosCorePrelude` and its Java-constructed
  standard `Context` prototype.
- Removed the obsolete tests that treated that static scaffold as the Core
  prelude.

### Changed

- Migrated remaining test execution-context construction to explicit
  `ProtosPrelude` state or, for the Core bootstrap test itself, to the
  irreducible root-backed bootstrap context.
- Updated the Core bootstrap architecture note to record that the Java-side
  `Context` scaffold has been retired.
- Project implementation version changed from `0.2.12-SNAPSHOT` to
  `0.2.13-SNAPSHOT`.

### Notes

- The standard `Context` identity is now constructed by
  `protos/lib/core/context.protos` through `ProtosCoreBootstrap`.
- No replacement process-global standard prototype is introduced.
- No normative specification change is introduced.


## [0.2.12-SNAPSHOT] - 2026-09-04

### Added

- Added explicit `ProtosPrelude` runtime state whose `Context` prototype is
  supplied rather than hardcoded.
- Added `ProtosCoreBootstrap`, which executes distributable
  `protos/lib/core/context.protos` through the ordinary source pipeline and
  returns a prelude backed by the resulting ordinary `Context` object.
- Added focused coverage for explicit prelude context creation and source-backed
  Core bootstrap.

### Changed

- Project implementation version changed from `0.2.11-SNAPSHOT` to
  `0.2.12-SNAPSHOT`.

### Notes

- Core bootstrap uses a short-lived internal root-backed context only to create
  the first standard `Context`; that bootstrap context is not a Protos module
  instance or a new standard prototype.
- No process-global mutable prelude state is introduced. `ProtosPrelude` is an
  explicit object so later Actor ownership can remain local.
- The older static `ProtosCorePrelude` remains for one migration increment and
  is not extended.
- No normative specification change is introduced.


## [0.2.11-SNAPSHOT] - 2026-09-04

### Added

- Added the first executable distributable Core source,
  `protos/lib/core/context.protos`.
- The Core source constructs the standard `Context` prototype as an ordinary
  Protos object with `Object` as its delegation parent.
- Added coverage that loads and executes that Core source through the ordinary
  source-file/compiler pipeline and observes the resulting `Context` binding.

### Changed

- Project implementation version changed from `0.2.10-SNAPSHOT` to `0.2.11-SNAPSHOT`.

### Notes

- The existing Java-side `Context` in `ProtosCorePrelude` remains temporary
  bootstrap scaffolding for now. This increment establishes the source-side
  replacement before changing activation construction to consume it.
- No special Core parser, AST, object constructor, or Java-side standard
  prototype was added.
- No normative specification change is introduced.


## [0.2.10-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosSourceFileLoader` to read UTF-8 Protos source from a host `Path`
  and compile it through the existing ordinary source compiler pipeline.
- Added focused coverage for successful UTF-8 loading and host file-loading
  failure propagation.

### Changed

- Project implementation version changed from `0.2.9-SNAPSHOT` to
  `0.2.10-SNAPSHOT`.

### Notes

- This is host-side implementation plumbing, not the Protos language I/O model.
  It does not expose `Path`, Java NIO, or file-loading behavior to Protos code.
- The loader deliberately does not define module identity, import caching,
  bootstrap order, or Core object identities. Those higher-level semantics stay
  outside this increment.
- This entry point is intended for the upcoming `protos/lib/core/` bootstrap
  loader and ordinary module-loading machinery.
- No normative specification change is introduced.


## [0.2.9-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosSourceCompiler`, a single source-to-execution entry point that
  composes the existing parser, canonicalizer, Truffle lowerer, and call-target
  construction pipeline.
- Added focused coverage proving compiled source preserves ordinary sequence,
  literal, object-construction, and parser-failure behavior.

### Changed

- Project implementation version changed from `0.2.8-SNAPSHOT` to
  `0.2.9-SNAPSHOT`.

### Notes

- This is implementation plumbing only. It introduces no new syntax, lookup,
  invocation, object, or bootstrap semantics.
- The new entry point is intended to be reused by the upcoming
  `protos/lib/core/` loader so Core source and user source travel through the
  same ordinary compiler pipeline.
- No normative specification change is introduced.


## [0.2.8-SNAPSHOT] - 2026-09-04

### Fixed

- Fixed `ProtosArgumentVectorNodeTest` to use the actual `SourceSpan` constructor
  instead of a nonexistent `SourceSpan.unknown()` helper.
- Project implementation version changed from `0.2.7-SNAPSHOT` to
  `0.2.8-SNAPSHOT`.

### Notes

- This is a test-compilation correction only; caller argument-vector semantics
  and implementation behavior are unchanged.
- No normative specification change is introduced.


## [0.2.7-SNAPSHOT] - 2026-09-04

### Added

- Added the caller-supplied positional-vector evaluation stage for future
  polymorphic invocation.
- Ordinary argument items are evaluated exactly once from left to right.
- Spread items require standard Array indexed state and append a shallow
  ascending-index snapshot at their exact evaluation position.
- Invalid spread sources signal Core `Error` immediately and prevent later
  argument evaluation.

### Changed

- Project implementation version changed from `0.2.6-SNAPSHOT` to `0.2.7-SNAPSHOT`.

### Notes

- This increment deliberately stops before Closure activation and parameter
  binding. It therefore does not need to manufacture the standard frozen
  `args` Array before the Core `Array` prototype is available from
  `protos/lib/core/`.
- No standard Array prototype or protocol behavior is hardcoded in Java.
- No normative specification change is introduced.


## [0.2.6-SNAPSHOT] - 2026-09-04

### Changed

- Removed temporary Java-side `Number`, `Integer`, and `Float` prototype objects
  from `ProtosCorePrelude`.
- Removed the Java-side numeric-family prototype resolver and the tests that
  treated those temporary objects as the implementation's standard numeric
  prelude.
- Kept only the explicitly documented temporary `Context` bootstrap scaffold.
- Project implementation version changed from `0.2.5-SNAPSHOT` to
  `0.2.6-SNAPSHOT`.

### Notes

- Numeric value representations and already-implemented numeric literal/identity
  semantics are unchanged.
- This corrects an implementation-architecture regression: standard numeric
  prototype objects belong to the future `protos/lib/core/` bootstrap path
  rather than a growing hardcoded Java standard library.
- No normative specification change is introduced.


## [0.2.5-SNAPSHOT] - 2026-09-04

### Added

- Added an internal standard-Array object representation with receiver-owned
  dense indexed state distinct from ordinary object slots.
- Array indexed reads and updates use mathematical `BigInteger` indices,
  preserve exact element references, reject negative/out-of-range indices, and
  never grow or create holes.
- Closed Arrays may replace existing indexed elements while frozen Arrays reject
  replacement before index validation.
- Added detached shallow indexed snapshots for future call-spread and iteration
  semantics.

### Changed

- `ProtosObjectValue` is now extensible internally so specialized object
  representations can retain the ordinary object/delegation/slot model without
  adding parallel language object categories.
- Project implementation version changed from `0.2.4-SNAPSHOT` to
  `0.2.5-SNAPSHOT`.

### Notes

- This increment adds representation only. It does not hardcode or expose the
  standard `Array` prelude object or its protocol methods in Java.
- The explicit parent supplied to each Array instance preserves the normative
  Array-factory rule and will allow `protos/lib/core/` to own the standard
  prototype object when Core bootstrap is available.
- No normative specification change is introduced.


## [0.2.4-SNAPSHOT] - 2026-09-04

### Added

- Added execution of canonical composition items inside object bodies.
- Composition evaluates its source first, copies effective local bindings into
  the object under construction, and makes successful contributions immediately
  visible to later body items.
- Direct local declarations reserve their names structurally across the complete
  receiving object body, excluding those names from every composition item.
- Composition conflicts and invalid non-ordinary composition sources now signal
  Core `Error` objects instead of leaking host exceptions.

### Changed

- Canonical object-body lowering now supplies the object's structural reservation
  set to each composition item while preserving strict left-to-right body
  execution.
- Project implementation version changed from `0.2.3-SNAPSHOT` to
  `0.2.4-SNAPSHOT`.

### Notes

- Composition reuses the existing atomic runtime contribution helper, so a
  conflicting item installs none of its effective bindings.
- `without` and `alias` remain blocked by B002 and are not exposed by this
  increment.
- No normative specification change is introduced.


## [0.2.3-SNAPSHOT] - 2026-09-04

### Added

- Added Truffle execution for canonical object expressions without composition.
- Bare object expressions now create fresh open ordinary objects delegating to
  the unique `Object` root.
- Explicit parent expressions are evaluated before object-body execution and
  their exact result becomes the constructed object's immutable delegation
  parent.
- Object bodies execute through construction activations, so local slot creation
  targets the new object while Closures skip the construction object as a lexical
  capture scope.

### Changed

- Canonical-to-Truffle lowering now accepts `CanonicalObject` when all body
  expressions are otherwise supported by the current execution slice.
- Project implementation version changed from `0.2.2-SNAPSHOT` to
  `0.2.3-SNAPSHOT`.

### Notes

- Canonical composition execution remains a separate following increment.
- No normative specification change is introduced; this implements the current
  object-model and execution-context contracts.


## [0.2.2-SNAPSHOT] - 2026-09-04

### Added

- Added explicit object-construction activations whose current context and
  receiver are the object under construction while Closure capture skips that
  construction object.
- Added transitive construction-scope skipping so Closures created inside nested
  object bodies capture only genuine enclosing lexical contexts.

### Changed

- Closure materialization now obtains its lexical capture chain from activation
  semantics instead of unconditionally capturing the activation's current
  context.
- Project implementation version changed from `0.2.1-SNAPSHOT` to
  `0.2.2-SNAPSHOT`.

### Notes

- This is implementation architecture for already-specified object-construction
  and lexical-capture semantics; no normative specification change is introduced.
- The new construction-activation boundary is intended to support subsequent
  canonical object execution and Core source bootstrap.


## [0.2.1-SNAPSHOT] - 2026-09-04

### Added

- Added executable numeric literal materialization and semantic identity execution.
- Added the runtime ordinary-object foundation with immutable delegation parents,
  local/delegated lookup, slot mutation, structural open/closed/frozen state,
  local-slot removal and snapshots, composition views, and atomic composition
  contributions.
- Added activation-context lookup ordering, Core error signaling, `this` and
  `context` execution, bare/member slot mutation, member lookup-home preservation,
  Closure materialization, and extracted-method binding foundations.
- Added empty-Sequence execution returning canonical `null`.
- Added canonical object composition-reservation discovery for direct local
  declarations.
- Added the initial standard `Context` bootstrap scaffold and fresh execution
  contexts delegating through `Context` to `Object`.
- Added the non-normative Core bootstrap architecture and reserved
  `protos/lib/core/` for standard objects and behavior implemented in Protos.

### Changed

- Project implementation version changed from `0.2.0-SNAPSHOT` to
  `0.2.1-SNAPSHOT`.
- Established an explicit repository rule requiring every committed executable
  implementation or distributable Core-library change to bump the Maven
  implementation patch version and add its corresponding root changelog entry.
- Limited Java-side standard-object construction to irreducible or explicitly
  temporary bootstrap scaffolding; ordinary Core behavior should move to
  `protos/lib/core/` as soon as it can be loaded faithfully.

### Notes

- This entry catches up implementation release metadata that was not maintained
  during the preceding incremental runtime/execution work.
- Protos Core language version remains 0.1.
- No normative language semantics are changed by the bootstrap architecture
  decision; observable Core behavior remains defined exclusively by `spec/`.
- The current Java-side `Context` bootstrap is temporary scaffolding until the
  Core source loader can construct it faithfully.

## [0.2.0-SNAPSHOT] - 2026-09-01

### Added

- Added canonical String literal execution as ordinary immutable Protos String values.

- Added initial Canonical-to-Truffle lowering for canonical `true`, `false`, and `null` singleton literals and non-empty sequences.

- Added a Truffle CallTarget entry point for executing Protos expression trees through the root-node boundary.
- Added the initial Truffle root execution boundary, delegating directly to the executable expression tree without introducing language-value semantics.

- Added the first executable Truffle node: non-empty expression sequences execute strictly left-to-right and return the final child result.
- Added the initial Truffle execution-node boundary with source-span preservation and the Truffle API dependency.
- Added canonical call-spread lowering with a contextual `CanonicalSpread(expression)` marker for ordinary, member, and super invocation arguments.
- Added dedicated canonical indexed-assignment lowering that preserves receiver/index/value evaluation structure and the distinct `atPut` assignment-result semantics.
- Added dedicated canonical lowering for non-spread `super.message(arguments...)` operations, preserving super lookup semantics separately from ordinary message sends.
- Added dedicated canonical intrinsic nodes for the reserved execution-context expressions `this`, `context`, and `args`, keeping them distinct from ordinary lexical lookup.
- Added canonical lowering for non-spread calls, preserving the semantic distinction between ordinary `Call(receiver, arguments)` and member message `Send(receiver, message, arguments)` forms.
- Added canonical object lowering with optional explicit parent, canonical object-body sequencing, and `Compose(object)` nodes for contextual composition items.
- Added canonical `Return(value)` lowering for the `^ expression` non-local return form.
- Added canonical `Create`/`Assign` lowering for bare and explicit-member slot writes while leaving indexed assignment for its distinct `atPut` semantics.
- Added canonical lowering for indexed reads as ordinary one-argument `at` message sends.
- Added canonical lowering for lazy `&&` and `||` as `and`/`or` message sends whose right-hand side is wrapped in a parameterless canonical Closure.
- Added canonical Closure and parameter lowering, including mandatory normalization of expression-bodied Closures to a one-expression canonical Sequence.
- Added canonical lowering for semantic equality/inequality and non-overridable identity/non-identity, with a dedicated canonical identity node.
- Added canonical lowering for the standard comparison operators `<`, `<=`, `>`, and `>=` as ordinary one-argument message sends.
- Added canonical lowering for the standard arithmetic operators `+`, `-`, `*`, `/`, and `%` as ordinary one-argument message sends.
- Added canonical lowering for custom symbolic binary operators as ordinary one-argument message sends while leaving standard binary operators for dedicated semantic lowering.
- Added canonical lowering for Core prefix `-` and `!` as ordinary zero-argument `negated` and `not` sends.
- Added the canonical semantic AST foundation and the first Surface AST canonicalization slice for literals, name lookup, grouping, member reads, and sequences.
- Established `.protos` as the project source-file extension and added initial non-normative tutorial, task-oriented example, and portable benchmark corpora.
- Added benchmark workloads for recursion, slots, closure and method calls, object creation, delegation depth, and monomorphic/polymorphic dispatch.
- Added parser support for same-line parameterless trailing closures as the final argument of ordinary call suffixes.
- Added closure surface AST integration and deterministic parsing for closure parameters, defaults, rest parameters, and braced or expression bodies.
- Added parser support for object expressions, parent expressions, and contextual object composition items.
- Added parser support for structural super message sends without making `super` a first-class expression.
- Added parsing for slot creation and assignment with grammar-defined target restrictions.
- Added deterministic parsing for the grammar-defined non-local return expression.
- Added parser support for the separate custom binary-operator precedence domain and its required standard/custom mixing errors.
- Added deterministic parsing for the standard unary and binary operator precedence ladder.
- Added parser support for same-line semicolon expression separators with the grammar-defined error cases.
- Extended the parser foundation with parenthesized expressions, member access, calls, indexing, argument spread, and leading-dot continuation.
- Added the first deterministic parser foundation with source-aware errors and a portable surface AST.
- Added source-aware lexer token occurrences with portable half-open source spans as parser infrastructure.
- Expanded lexer conformance coverage for raw Unicode scalar handling across every Core String form, including rejection of unpaired surrogates.
- Expanded lexer conformance coverage for numeric termination at structural delimiters, logical newlines, and standard and custom operators.
- Expanded lexer conformance coverage for uppercase radix-prefix commitment and case-insensitive exponent completion errors.
- Expanded lexer conformance coverage for malformed numeric/identifier adjacency across ASCII, reserved-word, underscore, and Unicode identifier continuations.
- Expanded lexer conformance coverage for single-line String raw-newline rejection, raw-source indentation matching, and interpolation-looking escape rejection.
- Expanded lexer conformance coverage for triple-double String CR/CRLF delimiter-newline handling and shared escape semantics.
- Expanded lexer conformance coverage for Unicode escape digit-count boundaries, hexadecimal case handling, and the closed String escape set.
- Expanded lexer conformance coverage for exact, case-sensitive reserved-word recognition and the closed Core v0.1 reserved-word set.
- Expanded lexer conformance coverage for triple-double String structural indentation, including absent and empty prefixes, blank-line exemption, and exact SPACE/TAB matching.
- Expanded lexer conformance coverage for the symbolic-operator alphabet, maximal-munch classification, and comment/operator lexical precedence.
- Expanded lexer conformance coverage for numeric separator placement, unsupported suffixes and radix floats, special-value identifiers, and valid token boundaries.
- Expanded lexer conformance coverage for line-comment termination, non-nesting block comments, first-delimiter closure, and comment delimiters inside Strings.
- Expanded lexer conformance coverage for String normalization independence, raw Unicode scalar content, quote-run boundaries, and the absence of triple-single String syntax.
- Expanded lexer conformance coverage for Core v0.1 String quote-run boundaries, unterminated String forms, and the closed whitespace rules.
- Added bundled Unicode 17.0.0 character-property and normalization data used by the lexer independently of the host JDK Unicode version.
- Added conformance tests against the official Unicode 17.0.0 `XID_Start`, `XID_Continue`, and normalization test data.

### Changed

- Decoupled canonical literal kinds from parser surface literal kinds with an explicit Surface-to-Canonical mapping.
- Corrected malformed parser imports introduced by slot-creation and assignment support.
- Restored the canonical NEWLINE token value after adding source-aware token occurrences.
- Fixed source-aware newline token occurrence emission so canonical NEWLINE lexemes remain valid Java Strings.
- Updated GitHub Actions CI to current supported `actions/checkout` and `actions/setup-java` major versions.
- Relicensed Protos from the Server Side Public License (SSPL) v1 to the OSI-approved Adaptive Public License 1.0 (APL-1.0). The complete license, including the completed Exhibit A, is in [LICENSE.TXT](LICENSE.TXT).
- Project implementation version changed from `0.1.6-SNAPSHOT` to `0.2.0-SNAPSHOT`.

### Notes

- Protos Core language version remains 0.1.
- No language semantics changed.

### Fixed

- Fixed parser newline continuation while a member suffix or structural `super` message send is necessarily incomplete.
- Fixed parser newline continuation after `...` in spread arguments and rest parameters, matching the grammar's necessarily-incomplete construct rule.
## [0.1.6-SNAPSHOT] - 2026-08-31

### Added

- Expanded lexer support to cover the lexical rules defined by the current Core v0.1 specification.
- Added lexer support for comments, Unicode-aware identifiers, reserved words, numeric literal forms, multiline strings, ellipsis, and custom symbolic operators.
- Added lexical validation for malformed string escape sequences and invalid Unicode scalar values.
- Added regression coverage for supplementary Unicode escape sequences.

### Changed

- Updated lexer tests to match the current specification for reserved words, period tokenization, and numeric literals adjacent to periods.
- Unicode escape decoding now preserves supplementary Unicode code points instead of truncating them to Java `char` values.

### Notes

- The lexer implementation is still under specification-compliance review.
- Unicode identifier handling requires further review for exact `XID_Start` and `XID_Continue` compliance.
- Specification changes are documented separately in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.5-SNAPSHOT] - 2026-08-31

### Notes
- No implementation changes in this session.
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.4-SNAPSHOT] - 2026-08-31

### Added
- Initial minimal Protos lexer implementation under `com.guillermomolina.protos.lexer`.
- Token model and token type definitions to represent the current lexical grammar.
- Basic lexical support for:
  - identifiers and reserved intrinsic keywords
  - numeric literals, including radix literals (`0x`, `0b`, `0o`)
  - string literals with escape handling
  - punctuation and structural tokens
  - operators and custom symbolic operators
  - newline-delimited token separation
- Focused lexer regression tests covering the implemented lexical behavior.
- Project licensing metadata by adding the license text in the then-current `LICENSE` file and referencing it from the README.

### Changed
- Added JUnit 5 to support lexer-focused test coverage.
- Updated project version from `0.1.3-SNAPSHOT` to `0.1.4-SNAPSHOT` as a conservative patch bump for the completed milestone.
- Documented the chosen license in the project README for this version.

### Notes
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).
