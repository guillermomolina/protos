# Offline XML profile acceptance

This directory contains original acceptance tooling metadata for the Protos
DTD-free, namespace-aware, nonvalidating XML profile. The normative authority is
[XML.md](../../../../../../spec/semantics/XML.md): XML 1.0 Fifth Edition,
Namespaces 1.0 Third Edition, semantic String input, no external entity resolution
and no implicit byte encoding detection. This work does not establish unrestricted
XML conformance or W3C certification.

## Implementation and evidence status

The maintainer supplied the following established evidence: 105/105 native XML
cases, 10/10 Test Tool integration tests, 54/54 Python projection tests, GraalVM
25.6.5.1 alignment and build PASS. Inventory is COMPLETE; generation and artifact
checking PASS. There are 2585 logical official cases, 2584 distinct input members,
1983 executable cases and 602 nonexecutable cases, with zero unresolved references
or classifications. Nonexecutable cases are not passing tests.

Historical standalone evidence includes the first regenerated case,
`rmt-ns10-017`, completing with exit status zero, followed by 65/65 Python
projection/executor tests PASS, artifact integrity PASS and official smoke 10/10
PASS. Those results predate the suite-native replacement. The later replacement
passed 57/57 Python projection tests and 27/27 focal Java tests, build, discovery,
generation and artifact checking. On 2026-11-28 the maintainer ran:

```sh
time bin/protos test --jobs 8 --directory protos/tests/conformance/library/xml
```

Reported: 2088 passed, 0 failed (105 ordinary XML, 74 SUPPORTED_ACCEPT, 237
SUPPORTED_REJECT, 1672 PROFILE_REJECT). All 34 progress groups passed. Wall time
157.435 seconds, user 30m43.845s, sys 0m36.013s. The former standalone duration
was an estimate, not a controlled comparison. This historical JVM evidence is
independent of generated NOT_EXECUTED metadata.

## Local input and licensing policy

Keep these acquired files unchanged and local:

- `upstream/w3c-20130923/xmlts20130923.zip`
- `upstream/w3c-20130923/LICENSE.html`
- `upstream/github-atom/announcements.atom`

[PROVENANCE.json](PROVENANCE.json) records the supplied acquisition identities,
URLs and observed HTTP metadata. The supplied Atom HTTP response Date is
`2026-11-28T05:58:23Z`, distinct from an independently observed local acquisition
timestamp (not supplied). The exact downloaded license URL is
`https://www.w3.org/Consortium/Legal/copyright-software-19980720`.
Do not infer archive acquisition times from filesystem metadata.

The original contributor notices remain inside the unchanged ZIP. In particular,
`xmlconf/xmltest/readme.html` contains historical restrictions on redistribution
in other forms. The general downloaded W3C license does not settle contributor
rights. Hashes identify bytes and do not grant permission. The reviewed XMLTEST
notice permits redistribution of the unchanged original xmltest.zip, but not
other forms of that collection. The IBM notice establishes no additional explicit
redistribution permission. The conservative publication policy keeps acquisitions,
fixture-bearing sources and their local manifest rows unversioned while retaining
all executable cases, including all 360 executable XMLTEST cases. Original tooling,
reviewed metadata and authored helpers may be published. This is not a finding of
general redistribution authorization. The notice hashes and contributor counts
are recorded in PROVENANCE.json.

Narrow directory-local ignore rules cover the acquired files, an extracted
`upstream/w3c-20130923/xmlconf/` tree, and the complete generated `work/` tree.
Fixture-bearing suite sources are additionally ignored by narrow `suite/accept-*.protos`,
`suite/reject-*.protos` and `suite/profile-reject-*.protos` rules.
Original tooling, harnesses, metadata and documentation remain versionable. Do
not automatically stage upstream content or generated artifacts.

## Offline catalog inventory

For a fresh checkout, acquire the W3C archive and its retained license from their
original URLs. Keep their bytes unchanged. From the repository root:

```sh
base=protos/tests/conformance/library/xml/official/upstream/w3c-20130923
mkdir -p "$base"
curl --fail --location --output "$base/xmlts20130923.zip" https://www.w3.org/XML/Test/xmlts20130923.zip
curl --fail --location --output "$base/LICENSE.html" https://www.w3.org/Consortium/Legal/copyright-software-19980720
python3 tools/xml/xml_test_projection.py inventory
```

The projection command never downloads inputs or runs Protos. It verifies the
complete ZIP SHA-256 before reading members and the exact license SHA-256.
Missing or changed W3C acquisitions fail explicitly. The independent historical
Atom capture is not required by inventory, generate, check or cleanup. Its exact
identity remains in PROVENANCE.json for separate real-consumer acceptance. A
new live response is not a substitute for the pinned historical response.
Archive members are read directly with `zipfile`; nothing is extracted. Absolute
paths, parent traversal, duplicate normalized names, encrypted entries, symlinks
and unsupported member types are rejected.

A dedicated Expat metadata reader starts at `xmlconf/xmlconf.xml`, loads the
archive-local `xmlconf/testcases.dtd`, and expands declared contributor catalogs
with `ExternalEntityParserCreate`. Physical entity bases and inherited element
`xml:base` are tracked separately. Network references, filesystem escapes and
unexpected entity destinations are rejected. Tests come only from catalog TEST
entries, never a filename scan. Nested profiles and descriptions including EM/B
text are retained. DTD defaults are applied; absent VERSION/EDITION mean all
versions/editions. Whitespace-separated lists are retained and validated.

The command writes these deterministic local artifacts:

- `work/INVENTORY.json`: ordered logical cases, complete metadata, classification
  reasons, input/output identities, catalog hashes and explicit inventory failures.
- `work/CASES.tsv`: one logical case per row in catalog order. Every cell is a
  JSON value with ASCII escapes, preserving null, tabs, line breaks and Unicode.
- `work/SHA256SUMS`: sorted referenced catalog, input and historical output hashes.

Logical case counts and distinct input-member counts are separate. Missing
catalog entities, fixture/output references and duplicate IDs are inventory
failures, not skipped successes. The report is retained and the command returns
nonzero when these failures occur. It permits only the exact verified reference repairs documented below. Historical OUTPUT/OUTPUT3
identities are retained without asserting canonical-serialization equivalence.

## Classification rules

Classification uses metadata and lexical source inspection before Protos results:

| Category | Meaning |
|---|---|
| SUPPORTED_ACCEPT | Applicable DTD-free valid or well-formed validity-only invalid case |
| SUPPORTED_REJECT | Applicable namespace-aware not-well-formed case |
| PROFILE_REJECT | String-representable document with a proven prohibited feature |
| OUTSIDE_STRING_DOMAIN | Strict decoding under the established transport interpretation cannot form a semantic Unicode scalar String |
| OUTSIDE_TRANSPORT_REQUIREMENT | Upstream byte/declaration consistency requirement excluded from semantic String parsing |
| OUTSIDE_XML_EDITION | Catalog requirement excludes the selected XML/Namespaces edition |
| OUTSIDE_VALIDATION_REQUIREMENT | Reserved for individually established validity-only obligations; does not turn well-formed invalid cases into rejections |
| REQUIRES_REVIEW | No justified exact profile expectation yet |

TYPE=invalid normally requires nonvalidating acceptance. Namespace-specific
invalid cases require individual reconciliation. TYPE=error is optional upstream
and requires review. Namespace-disabled expectations require review because
Protos always processes namespaces. Old errata are not blanket-excluded; explicit
edition restrictions apply. Only bracketed NameStartChar [4], NameChar [4a]
or Name [5] productions receive conservative historical-repertoire review;
Char [2] cases (including upstream cases labeled [3]) and AttValue [10]
are not name-repertoire questions. Exact input-bound adjudications establish
known current defects. Namespace editions and XML editions are distinguished.

The lexical scanner skips comments, PI, CDATA and quoted tag content before
recognizing real DOCTYPE markup. It neither validates a document nor processes
its DTD. A DOCTYPE case expects XML-owned rejection, without claiming an
unsupported-kind diagnostic takes precedence over an earlier syntax or limit
failure. No unsupported DTD is processed to discover a later defect.

Strict external fixture decoding records the selected codec and consumption of
at most one transport BOM. It preserves subsequent U+FEFF, physical CRLF/CR,
whitespace and XML-invalid but String-representable scalars. Decoding never uses
replacement. Ambiguous byte order, unsupported codecs and declaration/transport
inconsistencies require exact adjudication. A verified transport-only requirement
is nonexecutable, distinct from a String-domain impossibility. This transport interpretation adds no production
byte autodetection.

[classification-overrides.json](classification-overrides.json) binds each
individual adjudication to exact catalog identity, input member/hash and previous
mechanical classification. The inspected `rmt-ns10-004`, `005` and `006` fixtures
establish the opaque relative-URI/IRI rule, but all contain real DOCTYPEs. Their
complete originals therefore remain PROFILE_REJECT. No fixture is rewritten to
make the conditional namespace expectation applicable.

Unresolved classifications prevent a complete-acceptance claim. Inventory
construction is not executable acceptance, even when every reference resolves.

## Exact upstream reference reconciliation

The root declares `eduni-misc` as `eduni/misc/ht-bh.xml` but inserts it under
`xml:base="eduni/namespaces/misc/"`. Thus the original effective base is
`xmlconf/eduni/namespaces/misc/`; the intended fixture base is
`xmlconf/eduni/misc/`. The physical catalog identity remains
`xmlconf/eduni/misc/ht-bh.xml`.

[catalog-reconciliations.json](catalog-reconciliations.json) records these exact
repairs, with paths relative to `xmlconf/eduni/`:

| Case | Original target | Correct member |
|---|---|---|
| hst-bh-001 | namespaces/misc/001.xml | misc/001.xml |
| hst-bh-002 | namespaces/misc/002.xml | misc/002.xml |
| hst-bh-003 | namespaces/misc/003.xml | misc/003.xml |
| hst-bh-004 | namespaces/misc/004.xml | misc/004.xml |
| hst-bh-005 | namespaces/misc/005.xml | misc/005.xml |
| hst-bh-006 | namespaces/misc/006.xml | misc/006.xml |
| hst-lhs-007 | namespaces/misc/007.xml | misc/007.xml |
| hst-lhs-008 | namespaces/misc/008.xml | misc/008.xml |
| hst-lhs-009 | namespaces/misc/009.xml | misc/009.xml |

Each entry binds the pinned archive hash, full catalog identity, original URI,
inherited effective base and broken target to a replacement member and its
SHA-256 obtained from the local original. A repair requires the broken target to
be absent and the replacement bytes to match. Wrong archives, changed bindings,
present originals, missing/changed replacements, duplicate uses and unused entries
fail. There is no basename search or physical-directory fallback. The unchanged
ZIP and its catalogs remain the originals. INVENTORY.json and CASES.tsv retain
both the original target/base and the verified reconciliation. The pinned logical
count is 2585; the distinct member count is derived, never hardcoded.

## Individual classification evidence

Every override retains its actual member, SHA-256, pre-override mechanical
classification, exact reason and expected outcome. The complete fixture bodies
are never copied into versioned metadata.

- The nine XMLTEST negatives remain rejected: period/digit name starts, an invalid
  entity-reference name, an unquoted value, unmatched quotes, forbidden literal
  less-than or incomplete/bare ampersands. Their individual reasons are retained.
- The 28 requested OASIS `o-p03fail` cases (excluding 6) have an illegal literal
  control at original scalar offset zero. Exact scalar metadata covers U+0000
  through U+0004, U+0006 through U+0008, U+000B, U+000C, and U+000E through
  U+001F. All are outside Fifth Edition Char. No legal scalar is used as rejection
  evidence.
- `o-p04fail1/2/3` contain U+0040/U+0023/U+0024 respectively inside element names.
  `o-p05fail1/2/3` begin with digit/period/hyphen. `o-p05fail4/5` begin with U+0300
  and U+00B7 at offset one: these are NameChar additions, not NameStartChar.
  These exact originals remain rejected under Fifth Edition and NCName rules.
- `o-p10fail1/2/3` are AttValue defects (literal less-than, bare ampersand and
  mismatched quotes), independently of name history.
- The fifteen enumerated `rmt-ns10` validity-only cases are individually accepted:
  absent/default namespaces, legal declared prefixes, default clearing and
  rebinding, lexical prefix rebinding, the prebound/correctly declared xml prefix,
  xml2, and distinct expanded attributes. The inspected originals are DTD-free.
  Existing 004/005/006 remain PROFILE_REJECT because they contain DOCTYPEs.
- Namespace-disabled `o-p04pass1` contains an unbound prefix and a local name
  beginning U+0300. `o-p05pass1` contains malformed QNames (invalid local starts,
  multiple/lone colons) and unbound prefixes. Both are rejected for actual mandatory
  namespace constraints, not simply for NAMESPACE=no.
- `rmt-e2e-57` is accepted with its original TYPE=error retained. XML section 2.10
  permits reporting erroneous xml:space values; Protos imposes no application
  policy for them.
- `rmt-e2e-61` and `hst-lhs-007/008/009` retain upstream byte-level rejection as
  OUTSIDE_TRANSPORT_REQUIREMENT, with expected=null. Their explicit optional
  semantic interpretations have distinct identities in override metadata:
  strict UTF-8 for E61 and 007; strict UTF-16BE for 008; strict UTF-8 after the
  recorded incompatible two-byte prefix for 009. The first three semantic Strings
  are accepted; 009 still lacks the required XML declaration version and is
  rejected. These optional derived cases are recorded, not generated or counted
  as additional catalog cases. No production autodetection is introduced.
- All six `hst-bh` originals contain real DOCTYPEs and remain PROFILE_REJECT.
  The first four also contain out-of-range numeric references; the last two test
  validity obligations for namespace attributes. No original is edited to remove
  a DTD or to manufacture an executable malformed-reference-only case.

All review identities in the supplied first-slice report have individual
adjudications here. The established inventory has zero unresolved classifications.
Every nonexecutable category has expected=null.

## Suite-native projection and local registration

Python is solely acquisition metadata processing, deterministic fixture projection
and integrity tooling. It never executes Protos or launches test processes.
The existing `tools/xml/xml_test_projection.py` commands are `inventory`, `generate`
and read-only `check`; `cleanup` explicitly removes the locally owned generated
sources and registrations. Cleanup does not need the acquired archive or Atom.

Generation derives membership from the ordered inventory and creates one ordinary
`Test` per executable full catalog identity. Classification groups retain catalog
order independently, with at most 64 Tests per source:

| Classification | Tests | Sources |
|---|---:|---|
| SUPPORTED_ACCEPT | 74 | `suite/accept-00.protos` through `accept-01.protos` |
| SUPPORTED_REJECT | 237 | `suite/reject-00.protos` through `reject-03.protos` |
| PROFILE_REJECT | 1672 | `suite/profile-reject-00.protos` through `profile-reject-26.protos` |
| Total | 1983 | 33 |

The 602 nonexecutable cases remain in metadata with their reasons; they generate
no Tests. Each generated source imports `std:test/test` and `./Harness.protos`.
The canonical `Harness.protos` and `Fixtures.protos` live in `official/suite/`;
the harness imports `./Fixtures.protos`. Ordinary XML test sources import
`./official/suite/Fixtures.protos`. The Test Tool's direct-file SOURCE root is
the selected entry's parent directory, not the whole conformance corpus. Both
layouts keep imports within that root without granting new authority. Neither
helper is copied, generated, registered or removed by projection cleanup.
Fixture chunks are constructed inside each Test body, not during discovery.
Scalar escaping, exact partitioning and balanced String and Array reconstruction
preserve the original decoded inputs.

The harness retains eager parsing, whole-input and partitioned incremental
parsing, empty boundary feeds, exact XML-owned Error prototype/payload checks,
expected outcomes, diagnostic and event-prefix equivalence. Independent
`XML.encode` and eventWriter round-trips compare structure through the canonical
fixture helper, omitting only declaration encoding metadata. Parser and writer
instances remain independent. Unexpected Errors and assertions escape to the
ordinary Test Tool; successful execution returns canonical `true`.

`work/EXECUTION.json` retains every original input identity/hash, decoding,
classification, expectation and nonexecution reason. Executable entries record
`suite_source_path` (relative to the conformance corpus), `suite_source_sha256`
and exact `test_name`, plus canonical harness and Fixtures helper hashes.
Inventory and execution metadata never claim executable acceptance from generation or integrity checking.

Until contributor redistribution rights are reconciled, generated fixture-bearing
sources remain local and ignored. Generation materializes them and adds their
exact `library/xml/official/suite/<name>.protos` suite-native rows to the local
conformance manifest. Existing unrelated manifest bytes and registrations are
preserved; repeated generation is idempotent. Conflicting or duplicate rows,
auxiliary registrations, unowned files and modified owned sources are rejected.
Only successfully materialized generated sources receive new registrations.

The permanent versioned manifest has no XML official generated-source rows and
remains valid in a clean checkout without acquisitions. Do not publish local
registrations or fixture-bearing sources. Explicit `cleanup` removes only owned
local rows and hash-verified generated sources, preserving canonical helpers,
acquisitions, inventory/execution metadata, notices and historical reports.
Cleanup may be repeated. Regeneration after cleanup restores local registration.

Migration also removes the obsolete `work/programs/` entries and copied support
sources, after verifying the previous execution metadata, deterministic identity
filenames, exact support layout and recorded hashes. Unknown or modified legacy
files cause failure before deletion. Historical reports and raw acquisitions are
never migration targets. The suite-native generation, registration and artifact
checking were subsequently validated by the maintainer; cleanup before publication
remains a separate step.

`check` reconstructs expected artifacts in memory, compares source and metadata
bytes/hashes, checks exact local registrations and reports obsolete standalone or
unexpected suite sources without writing. Integrity checking is not acceptance.

## Ordinary Test Tool execution and remaining obligations

Once locally generated and registered, the 1983 Tests use the existing Test Tool
with its normal discovery, fresh semantic Process isolation, bounded parallel
case execution, progress and failure classification. Ordinary sources directly in
`library/xml` present as `conformance/standard-library/xml`. Official sources
directly in `library/xml/official/suite` present as
`conformance/standard-library/xml-official/<source>`, using each authoritative
filename without `.protos`, such as `accept-00`, `reject-03` or
`profile-reject-26`. Each generated source contains at most 64 Tests. Only
sources containing retained Cases contribute official subgroups, in descriptor
order and then first-retained-Case source order. Catalog identities and selector
directory structure do not determine group names. Unknown XML subdirectories
fail closed. Progress grouping is presentation only; Case identities, selection,
scheduling, Process isolation and result classification remain unchanged.
There is no XML scheduler, new owner or alternate execution mechanism. See the
[Test Tool guide](../../../../../../docs/guide/tools/test-tool.md).

`--file` selects a generated source's Tests; `--directory` selects the generated
suite or XML subtree recursively from the authoritative manifest. The existing
`make test-protos-lib LIB=xml` scope includes registered official descendants.
`--jobs` provides the existing bounded case parallelism. `--list-cases` supplies
opaque CaseRefs for exact `--case` selection; full catalog identities are Test
names, not substitutes for CaseRefs. Existing `--shard-count` and `--shard-run`
apply to the selected ordinary plan, without XML-specific sharding. All required
shards must pass against the same revision and selection before combining evidence.

The maintainer reported the ordinary XML scope passing 105/105 under the former
`conformance/standard-library/data-text-test` label in 117 seconds. This is prior
evidence preceding the new progress grouping. The later suite-native run
reported above validates the new grouping and the entire executable profile corpus.

The approved D268-R1 differential acceptance obligation remains pending and
unchanged. Mandatory real Atom and website-consumer parity also remain pending;
website parity requires the independently produced consumer result and exact
website revision, not generic parser agreement. Installed Native Image acceptance
has not been established. The conservative local-only publication policy does not
resolve contributor redistribution rights. The measured JVM profile-corpus
acceptance is not unrestricted XML conformance, W3C certification or completion
of all D268-R1 acceptance obligations.
