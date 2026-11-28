# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
# FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
# https://github.com/guillermomolina/protos
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.


"""Original synthetic tests for offline XML inventory, without external inputs."""

import io
from contextlib import redirect_stdout, redirect_stderr
import json
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "xml"))
import xml_test_projection as projection
import xml_projection_programs as programs


def archive(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as stream:
        for name, raw in entries:
            stream.writestr(name, raw)
    raw = output.getvalue()
    return projection.Archive(raw, expected=projection.digest(raw))


def test_entry(extra="", identity="original-case", uri="fixture.xml"):
    return ('<TEST ID="%s" URI="%s" TYPE="valid" SECTIONS="2.1" %s>'
            'Original <EM>synthetic <B>description</B></EM>.</TEST>') % (identity, uri, extra)


def reader_for(body, fixtures=()):
    source = '<TESTSUITE><TESTCASES>' + body + '</TESTCASES></TESTSUITE>'
    return projection.CatalogReader(archive([(projection.CATALOG, source.encode())] + list(fixtures)))


def case_for(kind="valid", **extra):
    values = dict(ID="synthetic", URI="fixture.xml", TYPE=kind, SECTIONS="2.1")
    values.update(extra)
    return projection.metadata(values)


def classify(raw=b"<r/>", kind="valid", **extra):
    decoding, text = projection.decode_fixture(raw)
    return projection.classify(case_for(kind, **extra), decoding, text)


class ArchiveTests(unittest.TestCase):
    def test_pinned_hash_checked_before_zip_parsing(self):
        with self.assertRaisesRegex(projection.ProjectionError, "identity mismatch"):
            projection.Archive(b"not even a ZIP")

    def test_missing_and_mismatched_local_input(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "input"
            with self.assertRaisesRegex(projection.ProjectionError, "unavailable"):
                projection.pinned_read(target, "0" * 64)
            target.write_bytes(b"original")
            with self.assertRaisesRegex(projection.ProjectionError, "mismatch"):
                projection.pinned_read(target, "0" * 64)
            with self.assertRaisesRegex(projection.ProjectionError, "mismatch"):
                projection.pinned_read(target, projection.digest(b"original"), 1)

    def test_unsafe_member_names(self):
        for name in ("/xmlconf/a", "xmlconf/../a", "elsewhere/a", "C:/a", "xmlconf\\a"):
            with self.subTest(name=name), self.assertRaises(projection.ProjectionError):
                archive([(name, b"x")])

    def test_duplicate_normalized_members(self):
        with self.assertRaisesRegex(projection.ProjectionError, "duplicate normalized"):
            archive([("xmlconf/a", b"a"), ("xmlconf/./a", b"b")])

    def test_symlink_rejected(self):
        member = zipfile.ZipInfo("xmlconf/link")
        member.create_system = 3
        member.external_attr = (stat.S_IFLNK | 0o777) << 16
        with self.assertRaisesRegex(projection.ProjectionError, "member type"):
            archive([(member, b"target")])


class CatalogTests(unittest.TestCase):
    def test_local_external_entities_nested_base_and_dtd_defaults(self):
        root = '''<!DOCTYPE TESTSUITE SYSTEM "testcases.dtd" [
            <!ENTITY contributor SYSTEM "catalog/part.xml">]>
            <TESTSUITE PROFILE="Original suite"><TESTCASES xml:base="fixtures/" PROFILE="Original contributor">
            &contributor;</TESTCASES></TESTSUITE>'''
        dtd = '''<!ELEMENT TESTSUITE ANY><!ELEMENT TESTCASES ANY><!ELEMENT TEST ANY>
            <!ATTLIST TEST ENTITIES CDATA "none" NAMESPACE CDATA "yes" RECOMMENDATION CDATA "XML1.0">'''
        part = '<TESTCASES xml:base="nested/">' + test_entry() + '</TESTCASES>'
        reader = projection.CatalogReader(archive([
            (projection.CATALOG, root.encode()), (projection.DTD, dtd.encode()),
            ("xmlconf/catalog/part.xml", part.encode()), ("xmlconf/fixtures/nested/fixture.xml", b"<r/>")]))
        cases = reader.parse()
        self.assertEqual(reader.failures, [])
        case = cases[0]
        self.assertEqual(case["identity"], "xmlconf/catalog/part.xml#original-case")
        self.assertEqual(case["effective_base"], "xmlconf/fixtures/nested/")
        self.assertEqual(case["input_member"], "xmlconf/fixtures/nested/fixture.xml")
        self.assertEqual(case["ancestry"], ["Original suite", "Original contributor"])
        self.assertEqual(case["description"], "Original synthetic description.")
        for key, value in projection.DEFAULTS.items():
            self.assertEqual(case[key], value)
        self.assertIsNone(case["VERSION"])
        self.assertIsNone(case["EDITION"])

    def test_external_reference_cannot_escape_archive(self):
        for reference in ("https://example.invalid/x.xml", "../../escape.xml", "file:///tmp/x.xml"):
            raw = ('<!DOCTYPE TESTSUITE [<!ENTITY part SYSTEM "%s">]>'
                   '<TESTSUITE><TESTCASES>&part;</TESTCASES></TESTSUITE>') % reference
            reader = projection.CatalogReader(archive([(projection.CATALOG, raw.encode())]))
            with self.subTest(reference=reference), self.assertRaises(projection.ProjectionError):
                reader.parse()

    def test_unresolved_reference_is_failure_and_case_is_retained(self):
        reader = reader_for(test_entry())
        cases = reader.parse()
        self.assertEqual(len(cases), 1)
        self.assertEqual(reader.failures[0]["kind"], "UNRESOLVED_CASE_REFERENCE")
        self.assertIsNone(cases[0]["input_sha256"])
        decoding = {"status": "review", "reason": "missing"}
        self.assertEqual(projection.classify(cases[0], decoding, None)["classification"], "REQUIRES_REVIEW")

    def test_missing_entity_does_not_invent_catalog_cases(self):
        raw = (b'<!DOCTYPE TESTSUITE [<!ENTITY part SYSTEM "missing.xml">]>'
               b'<TESTSUITE><TESTCASES>&part;</TESTCASES></TESTSUITE>')
        reader = projection.CatalogReader(archive([(projection.CATALOG, raw)]))
        self.assertEqual(reader.parse(), [])
        self.assertEqual(reader.failures[0]["kind"], "UNRESOLVED_CATALOG_REFERENCE")

    def test_duplicate_logical_identity_is_visible(self):
        reader = reader_for(test_entry() + test_entry(), [("xmlconf/fixture.xml", b"<r/>")])
        self.assertEqual(len(reader.parse()), 2)
        self.assertEqual(len(reader.failures), 2)
        self.assertTrue(all(c["inventory_problem"] for c in reader.cases))

    def test_unknown_metadata_rejected(self):
        for key, value in (("TYPE", "maybe"), ("VERSION", "2.0"), ("EDITION", "6"),
                           ("NAMESPACE", "maybe"), ("RECOMMENDATION", "future")):
            with self.subTest(key=key), self.assertRaises(projection.ProjectionError):
                case_for(**{key: value})

    def test_version_and_edition_lists(self):
        case = case_for(VERSION="1.0 1.1")
        self.assertEqual(case["version_list"], ["1.0", "1.1"])
        case = case_for(VERSION="1.0", EDITION="3 4 5")
        self.assertEqual(case["edition_list"], ["3", "4", "5"])
        with self.assertRaises(projection.ProjectionError):
            case_for(VERSION="1.0 1.1", EDITION="5")


class ClassificationTests(unittest.TestCase):
    def test_type_semantics(self):
        for kind, expected in (("valid", "SUPPORTED_ACCEPT"), ("invalid", "SUPPORTED_ACCEPT"),
                               ("not-wf", "SUPPORTED_REJECT"), ("error", "REQUIRES_REVIEW")):
            self.assertEqual(classify(kind=kind)["classification"], expected)

    def test_namespace_invalid_and_disabled_need_review(self):
        self.assertEqual(classify(kind="invalid", RECOMMENDATION="NS1.0")["classification"], "REQUIRES_REVIEW")
        self.assertEqual(classify(NAMESPACE="no")["classification"], "REQUIRES_REVIEW")

    def test_editions_and_earlier_errata(self):
        self.assertEqual(classify(VERSION="1.1")["classification"], "OUTSIDE_XML_EDITION")
        self.assertEqual(classify(VERSION="1.0", EDITION="1 2 3 4")["classification"], "OUTSIDE_XML_EDITION")
        self.assertEqual(classify(VERSION="1.0", EDITION="4 5")["classification"], "SUPPORTED_ACCEPT")
        self.assertEqual(classify(RECOMMENDATION="XML1.0-errata2e")["classification"], "SUPPORTED_ACCEPT")
        self.assertEqual(classify(RECOMMENDATION="NS1.0", EDITION="3")["classification"], "REQUIRES_REVIEW")

    def test_literal_doctype_in_lexical_context(self):
        for text in ('<!-- <!DOCTYPE r> --><r/>', '<r><![CDATA[<!DOCTYPE r>]]></r>',
                     '<?p <!DOCTYPE r> ?><r/>', '<r a="<!DOCTYPE r>"/>'):
            self.assertFalse(projection.lexical_features(text)["doctype"])
        self.assertEqual(classify(b'<!--inert--><!DOCTYPE r [<!ELEMENT r ANY>]><r/>')["classification"], "PROFILE_REJECT")

    def test_only_one_transport_bom_consumed(self):
        decoding, text = projection.decode_fixture(b"\xef\xbb\xbf\xef\xbb\xbf<r/>")
        self.assertTrue(decoding["transport_bom_consumed"])
        self.assertEqual(text, "\ufeff<r/>")
        raw = b"\xff\xfe" + "\ufeff<r/>".encode("utf-16-le")
        self.assertEqual(projection.decode_fixture(raw)[1], "\ufeff<r/>")

    def test_strict_decoding_and_string_representable_invalid_scalars(self):
        self.assertEqual(classify(b"<r>\xff</r>")["classification"], "OUTSIDE_STRING_DOMAIN")
        self.assertEqual(classify(b"<r>\xed\xa0\x80</r>")["classification"], "OUTSIDE_STRING_DOMAIN")
        for text in ("<r>\x00</r>", "<r>\ufffe</r>", "<r>\U0001f600</r>"):
            self.assertEqual(projection.decode_fixture(text.encode())[1], text)
        self.assertEqual(classify(b'<?xml version="1.0" encoding="not-a-codec"?><r/>')["classification"], "REQUIRES_REVIEW")

    def test_physical_crlf_and_declared_legacy_encoding_preserved(self):
        raw = b'<?xml version="1.0" encoding="iso-8859-1"?><r>\xe9\r\n\r</r>'
        decoding, text = projection.decode_fixture(raw)
        self.assertEqual(decoding["encoding"], "iso-8859-1")
        self.assertEqual(text, raw.decode("iso-8859-1"))

    def test_transport_mismatch_not_silently_promoted_to_string_defect(self):
        raw = b'\xef\xbb\xbf<?xml version="1.0" encoding="iso-8859-1"?><r/>'
        self.assertEqual(classify(raw, kind="not-wf")["classification"], "REQUIRES_REVIEW")

    def test_future_declaration_has_explicit_profile_expectation(self):
        self.assertEqual(classify(b'<?xml version="1.7"?><r/>')["classification"], "PROFILE_REJECT")


class OutputTests(unittest.TestCase):
    def test_inventory_serialization_is_stable_and_escaped(self):
        reader = reader_for(test_entry(), [("xmlconf/fixture.xml", b"<r/>")])
        cases = reader.parse()
        cases[0].update(description="original\ttext\n\U0001f600", decoding={},
                        classification="SUPPORTED_ACCEPT", reason="original", reference="original", expected="accept")
        report = {"cases": cases, "catalog_members": reader.catalog_members}
        first = projection.artifacts(report)
        self.assertEqual(first, projection.artifacts(report))
        self.assertNotIn("\U0001f600".encode(), first["CASES.tsv"])
        lines = first["CASES.tsv"].decode().splitlines()
        self.assertEqual(len(lines), 2)
        self.assertEqual(len(lines[0].split("\t")), len(lines[1].split("\t")))
        self.assertIn(b"\\ttext\\n", first["CASES.tsv"])

    def test_override_is_bound_to_exact_input_and_previous_classification(self):
        case = {"identity": "catalog#original", "input_member": "xmlconf/input.xml",
                "input_sha256": "0" * 64, "classification": "PROFILE_REJECT"}
        override = dict(case, previous_classification="PROFILE_REJECT", final_classification="PROFILE_REJECT",
                        expected="reject", normative_reason="original reason", reference="spec/semantics/XML.md §1")
        override.pop("classification")
        projection.apply_overrides([case], {case["identity"]: override})
        self.assertEqual(case["expected"], "reject")
        for key, value in (("input_sha256", "1" * 64), ("previous_classification", "REQUIRES_REVIEW"),
                           ("expected", "accept"), ("reference", "")):
            with self.subTest(key=key), self.assertRaises(projection.ProjectionError):
                projection.apply_overrides([case], {case["identity"]: dict(override, **{key: value})})

    def test_duplicate_override_json_keys_rejected(self):
        with self.assertRaises(projection.ProjectionError):
            json.loads('{"original": 1, "original": 2}', object_pairs_hook=projection.unique_object)


class ReconciliationTests(unittest.TestCase):
    def reader(self, **changes):
        source = ('<TESTSUITE><TESTCASES xml:base="broken/">' + test_entry() +
                  '</TESTCASES></TESTSUITE>')
        members = [(projection.CATALOG, source.encode()), ("xmlconf/correct/fixture.xml", b"<r/>")]
        if changes.pop("original_present", False):
            members.append(("xmlconf/broken/fixture.xml", b"<r/>"))
        if changes.pop("replacement_missing", False):
            members.pop()
        arc = archive(members)
        identity = projection.CATALOG + "#original-case"
        entry = dict(identity=identity, archive_sha256=arc.sha256, catalog_member=projection.CATALOG,
                     URI="fixture.xml", effective_base="xmlconf/broken/",
                     original_target="xmlconf/broken/fixture.xml", replacement_member="xmlconf/correct/fixture.xml",
                     replacement_sha256=projection.digest(b"<r/>"), reason="Original synthetic base defect")
        entry.update(changes)
        return projection.CatalogReader(arc, {identity: entry})

    def test_exact_repair_preserves_original_base_and_target(self):
        reader = self.reader()
        case = reader.parse()[0]
        self.assertEqual(reader.failures, [])
        self.assertEqual(case["effective_base"], "xmlconf/broken/")
        self.assertEqual(case["original_input_target"], "xmlconf/broken/fixture.xml")
        self.assertEqual(case["input_member"], "xmlconf/correct/fixture.xml")
        self.assertEqual(case["reconciliation"]["replacement_sha256"], case["input_sha256"])

    def test_archive_mismatch(self):
        with self.assertRaisesRegex(projection.ProjectionError, "archive mismatch"):
            self.reader(archive_sha256="0" * 64)

    def test_original_unexpectedly_present(self):
        with self.assertRaisesRegex(projection.ReconciliationError, "original target"):
            self.reader(original_present=True).parse()

    def test_replacement_missing_or_hash_changed(self):
        for change in ({"replacement_missing": True}, {"replacement_sha256": "0" * 64}):
            with self.subTest(change=change), self.assertRaises(projection.ReconciliationError):
                self.reader(**change).parse()

    def test_stale_binding(self):
        for change in ({"URI": "other.xml"}, {"effective_base": "xmlconf/elsewhere/"},
                       {"catalog_member": "xmlconf/other.xml"}, {"original_target": "xmlconf/other.xml"}):
            with self.subTest(change=change), self.assertRaises(projection.ReconciliationError):
                self.reader(**change).parse()

    def test_unused_and_duplicate_repair(self):
        reader = self.reader()
        original = next(iter(reader.reconciliations.values()))
        other = dict(original, identity=projection.CATALOG + "#absent")
        reader.reconciliations[other["identity"]] = other
        with self.assertRaisesRegex(projection.ProjectionError, "unused reconciliation"):
            reader.parse()
        reader = self.reader()
        case = reader.parse()[0]
        with self.assertRaisesRegex(projection.ProjectionError, "duplicate reconciliation"):
            reader.reconciled_target(case, case["original_input_target"])

    def test_unrelated_missing_reference_stays_a_failure(self):
        reader = self.reader()
        reader.reconciliations = {}
        self.assertEqual(len(reader.parse()), 1)
        self.assertEqual(reader.failures[0]["kind"], "UNRESOLVED_CASE_REFERENCE")


class AdjudicationTests(unittest.TestCase):
    def override(self, source, kind="not-wf", final="SUPPORTED_REJECT", **metadata):
        raw = source.encode("utf-8")
        case = case_for(kind, **metadata)
        case.update(identity="xmlconf/synthetic.xml#case", input_member="xmlconf/synthetic.xml",
                    input_sha256=projection.digest(raw))
        case.update(classify(raw, kind=kind, **metadata))
        expected = "accept" if final == "SUPPORTED_ACCEPT" else "reject" if final in programs.EXECUTABLE else None
        entry = dict(identity=case["identity"], input_member=case["input_member"],
                     input_sha256=case["input_sha256"], previous_classification=case["classification"],
                     final_classification=final, expected=expected,
                     normative_reason="Original synthetic fixture inspected against the current production.",
                     reference="spec/semantics/XML.md sections 4, 5, 6")
        return case, entry

    def test_char_and_attvalue_do_not_inherit_name_ambiguity(self):
        for raw, section in ((b"<x>\x00</x>", "2.3 [2]"), (b"<x>\x01</x>", "2.3 [3]"),
                             (b'<x a="<"/>', "2.3 [10]")):
            self.assertEqual(classify(raw, kind="not-wf", SECTIONS=section)["classification"], "SUPPORTED_REJECT")
        for section in ("2.3 [4]", "2.3 [4a]", "2.3 [5]"):
            self.assertEqual(classify(kind="not-wf", SECTIONS=section)["classification"], "REQUIRES_REVIEW")
        self.assertFalse(projection.historical_name_production("12.3 [40], [50]"))

    def test_established_name_start_and_name_char_defects(self):
        for source in ("<7x/>", "<.x/>", "<-x/>", "<\u0301x/>", "<\u00b7x/>", "<x@/>"):
            case, entry = self.override(source, SECTIONS="2.3 [4] [5]")
            self.assertEqual(case["classification"], "REQUIRES_REVIEW")
            projection.apply_overrides([case], {case["identity"]: entry})
            self.assertEqual(case["expected"], "reject")

    def test_currently_legal_historical_name_can_be_accepted(self):
        # U+037F is now NameStartChar. The original synthetic input has no DTD.
        case, entry = self.override("<\u037fx/>", final="SUPPORTED_ACCEPT", SECTIONS="2.3 [4]")
        projection.apply_overrides([case], {case["identity"]: entry})
        self.assertEqual(case["expected"], "accept")

    def test_namespace_validity_only_acceptance_is_individual(self):
        case, entry = self.override('<x xmlns:q="urn:synthetic" q:a="1" a="2"/>',
                                    kind="invalid", final="SUPPORTED_ACCEPT", RECOMMENDATION="NS1.0")
        self.assertEqual(case["classification"], "REQUIRES_REVIEW")
        projection.apply_overrides([case], {case["identity"]: entry})
        self.assertEqual(case["expected"], "accept")

    def test_transport_only_is_not_string_domain_or_successful_rejection(self):
        case, entry = self.override('<?xml version="1.0" encoding="UTF-16"?><x/>',
                                    final="OUTSIDE_TRANSPORT_REQUIREMENT")
        self.assertEqual(case["classification"], "REQUIRES_REVIEW")
        projection.apply_overrides([case], {case["identity"]: entry})
        self.assertEqual(case["classification"], "OUTSIDE_TRANSPORT_REQUIREMENT")
        self.assertIsNone(case["expected"])
        self.assertEqual(classify(b"<x>\xff</x>")["classification"], "OUTSIDE_STRING_DOMAIN")

    def test_nonexecutable_override_cannot_claim_an_outcome(self):
        for final in projection.CATEGORIES - programs.EXECUTABLE.keys():
            case, entry = self.override("<x/>", final=final)
            for expected in ("accept", "reject"):
                with self.subTest(final=final, expected=expected), self.assertRaisesRegex(projection.ProjectionError, "contradictory"):
                    projection.apply_overrides([case], {case["identity"]: dict(entry, expected=expected)})


class ProgramTests(unittest.TestCase):
    def case(self, identity="xmlconf/synthetic.xml#original", category="SUPPORTED_ACCEPT"):
        return dict(identity=identity, input_member="xmlconf/synthetic.xml", input_archive_member="xmlconf/synthetic.xml",
                    input_sha256=projection.digest(b"<x/>"), decoding={"status": "decoded", "encoding": "utf-8"},
                    classification=category, expected=programs.EXECUTABLE.get(category), reason="Original synthetic input")

    def artifacts(self, cases=None):
        return programs.execution_artifacts({"cases": cases or [self.case()], "inventory_failures": []}, lambda case: "<x/>")

    def test_exact_escaping_and_physical_lines(self):
        self.assertEqual(programs.literal('a\r\nb\r\t\x00"\\\ufeff\U0001f642'),
                         '"a\\r\\nb\\r\\t\\u{0}\\"\\\\\\u{FEFF}\\u{1F642}"')
        with self.assertRaises(ValueError):
            programs.literal("\ud800")

    def test_literal_pieces_are_bounded_and_balanced(self):
        text = "\U0001f642" * 2048
        expression = programs.literal(text)
        self.assertEqual(expression.count("\\u{1F642}"), len(text))
        self.assertLess(max(map(len, expression.split('.concat('))), 1100)
        depth = maximum = 0
        for character in expression:
            if character == '(':
                depth += 1
                maximum = max(maximum, depth)
            elif character == ')':
                depth -= 1
        self.assertEqual(depth, 0)
        self.assertLess(maximum, 12)

    def test_scalar_partitions_reconstruct_without_normalization(self):
        for text in ("", "<x>\r\n\x00\ufeff\U0001f642</x>", "ab\r\n\U0001f642" * 1000):
            chunks = programs.partition_chunks(text)
            self.assertEqual("".join(chunks), text)
            self.assertTrue(all(len(c) <= 256 for c in chunks))
            if len(text) <= 256:
                self.assertTrue(all(len(c) == 1 for c in chunks))

    def test_suite_membership_identity_order_and_bounds(self):
        # Interleave classifications to distinguish catalog order from name sorting.
        cases = []
        counts = {"SUPPORTED_ACCEPT": 74, "SUPPORTED_REJECT": 237, "PROFILE_REJECT": 1672}
        for index in range(1672):
            for category, count in counts.items():
                if index < count:
                    cases.append(self.case("catalog.xml#%s-%04d" % (category, count - index), category))
        cases.extend(self.case("catalog.xml#excluded-%d" % i, "OUTSIDE_STRING_DOMAIN") for i in range(602))
        data = self.artifacts(cases)
        self.assertEqual(data, self.artifacts(cases))
        names = ["suite/%s-%02d.protos" % (prefix, i)
                 for prefix, count in (("accept", 2), ("reject", 4), ("profile-reject", 27))
                 for i in range(count)]
        self.assertEqual(list(data), names + ["EXECUTION.json"])
        metadata = json.loads(data["EXECUTION.json"])
        self.assertEqual([r["identity"] for r in metadata["cases"]], [c["identity"] for c in cases])
        seen = set()
        for name in names:
            records = [r for r in metadata["cases"] if r["suite_source_path"] == programs.MANIFEST_PREFIX + name]
            self.assertLessEqual(len(records), 64)
            self.assertEqual(len({r["classification"] for r in records}), 1)
            category = records[0]["classification"]
            source = data[name].decode("ascii")
            self.assertEqual(source.count("    Test("), len(records))
            self.assertIn('H: import("./Harness.protos")', source)
            self.assertIn('Test: import("std:test/test")', source)
            self.assertTrue(source.endswith("]\ntests.freeze()\n"))
            identities = {r["identity"] for r in records}
            self.assertTrue(seen.isdisjoint(identities))
            seen.update(identities)
            expected_group = [c["identity"] for c in cases if c["classification"] == category]
            positions = [expected_group.index(r["identity"]) for r in records]
            self.assertEqual(positions, list(range(positions[0], positions[0] + len(records))))
            for record in records:
                self.assertEqual(record["test_name"], record["identity"])
                self.assertEqual(record["suite_source_sha256"], projection.digest(data[name]))
                self.assertEqual(record["input_sha256"], projection.digest(b"<x/>"))
                self.assertIn('    Test(%s, () => {' % programs.literal(record["identity"]), source)
        self.assertEqual(len(seen), 1983)
        excluded = [r for r in metadata["cases"] if r["test_name"] is None]
        self.assertEqual(len(excluded), 602)
        self.assertTrue(all(r["suite_source_path"] is None and r["nonexecution_reason"] for r in excluded))
        self.assertFalse(metadata["complete_acceptance_claim_permitted"])
        self.assertEqual(metadata["executable_acceptance"], "NOT_EXECUTED")

    def test_fixture_construction_is_inside_each_test_and_preserves_exact_identity(self):
        cases = [self.case('catalog.xml#quote"\n\U0001f642'), self.case("catalog.xml#second")]
        data = self.artifacts(cases)
        source = data["suite/accept-00.protos"].decode("ascii")
        self.assertNotIn("chunks:", source.split("tests: [")[0])
        bodies = source.split("    Test(")[1:]
        self.assertEqual(len(bodies), 2)
        for case, body in zip(cases, bodies):
            self.assertTrue(body.startswith(programs.literal(case["identity"]) + ", () => {"))
            self.assertIn("        chunks: Map()", body)
            self.assertIn('        chunks[0] = "<"', body)
            self.assertIn("source: H.join(chunks, 0, 4)", body)
            self.assertIn('H.run(%s, "accept", source, chunks, 4)' % programs.literal(case["identity"]), body)
        self.assertNotIn(", print)", source)

    def test_nonexecutable_sources_and_invalid_identity_expectations(self):
        cases = [self.case(str(i), category) for i, category in enumerate(
            sorted(projection.CATEGORIES - programs.EXECUTABLE.keys()))]
        with patch.object(programs, "partition_chunks") as partition:
            self.assertEqual(set(self.artifacts(cases)), {"EXECUTION.json"})
            partition.assert_not_called()
        case = self.case()
        for report in ({"cases": [case], "inventory_failures": ["missing"]},
                       {"cases": [case, case], "inventory_failures": []},
                       {"cases": [dict(case, inventory_problem="missing")], "inventory_failures": []},
                       {"cases": [dict(case, expected="reject")], "inventory_failures": []},
                       {"cases": [dict(case, classification="REQUIRES_REVIEW")], "inventory_failures": []}):
            with self.assertRaises(ValueError):
                programs.execution_artifacts(report, lambda c: "<x/>")

    def test_harness_completion_and_canonical_source_provenance(self):
        harness = (projection.ROOT / projection.OFFICIAL / "suite/Harness.protos").read_bytes()
        text = harness.decode("utf-8")
        self.assertIn("run: (identity, expected, source, chunks, count) => {", text)
        self.assertTrue(text.rstrip().endswith("true\n}"))
        self.assertIn('F: import("./Fixtures.protos")', text)
        self.assertIn("XML.XmlError.handle(", text)
        self.assertNotIn("\n    Error.handle(", text)
        for obsolete in ("PROTOS_XML_RESULT", "JSON:", "nullableString", "nullableInteger", "output("):
            self.assertNotIn(obsolete, text)
        report = {"cases": [self.case()], "inventory_failures": []}
        first = programs.execution_artifacts(report, lambda c: "<x/>", harness=harness)
        changed = programs.execution_artifacts(report, lambda c: "<x/>", harness=harness + b"\n")
        self.assertEqual(json.loads(first["EXECUTION.json"])["harness_sha256"], projection.digest(harness))
        self.assertNotEqual(first["EXECUTION.json"], changed["EXECUTION.json"])
        self.assertEqual(first["suite/accept-00.protos"], changed["suite/accept-00.protos"])
        fixtures = b"// Original synthetic fixture helper\n"
        original = programs.execution_artifacts(report, lambda c: "<x/>", fixtures=fixtures)
        edited = programs.execution_artifacts(report, lambda c: "<x/>", fixtures=fixtures + b"\n")
        self.assertEqual(json.loads(original["EXECUTION.json"])["fixtures_sha256"], projection.digest(fixtures))
        self.assertNotEqual(original["EXECUTION.json"], edited["EXECUTION.json"])
        self.assertEqual(original["suite/accept-00.protos"], edited["suite/accept-00.protos"])
        for registration in programs.manifest_lines(first):
            self.assertEqual(registration.count("\t"), 2)
            self.assertNotIn("\\t", registration)
        self.assertEqual(programs.manifest_lines(first),
                         ["library/xml/official/suite/accept-00.protos\tsuite-native\t-"])
        self.assertEqual(programs.manifest_lines(["suite/Harness.protos", "suite/Fixtures.protos", "../Transfer.protos"]), [])


class LocalLifecycleTests(unittest.TestCase):
    case = ProgramTests.case
    artifacts = ProgramTests.artifacts

    def layout(self, root):
        work = root / "official/work"
        manifest = root / "manifest.tsv"
        manifest.write_bytes(b"# Original manifest\r\nother.protos\tsuite-native\t-\r\n")
        return work, manifest

    def snapshot(self, root):
        return {p.relative_to(root).as_posix(): (p.read_bytes(), p.stat().st_mtime_ns)
                for p in root.rglob("*") if p.is_file()}

    def test_local_registration_idempotence_and_cleanup_preserve_unrelated_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work, manifest = self.layout(root)
            original = manifest.read_bytes()
            expected = self.artifacts()
            programs.write_artifacts(work, expected, manifest)
            registered = manifest.read_bytes()
            self.assertEqual(registered, original + b"library/xml/official/suite/accept-00.protos\tsuite-native\t-\n")
            programs.write_artifacts(work, expected, manifest)
            self.assertEqual(manifest.read_bytes(), registered)
            self.assertEqual(programs.compare_artifacts(work, expected, manifest), [])
            # Keep metadata, acquisitions, historical evidence and authored helpers.
            keep = [work / "INVENTORY.json", work / "reports/smoke.json",
                    work.parent / "suite/Harness.protos",
                    work.parent / "suite/Fixtures.protos",
                    work.parent / "upstream/original.zip",
                    work.parent / "suite/unrelated.txt"]
            for path in keep:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"unchanged original")
            metadata = (work / "EXECUTION.json").read_bytes()
            programs.cleanup(work, manifest)
            programs.cleanup(work, manifest)
            self.assertEqual(manifest.read_bytes(), original)
            self.assertFalse((work.parent / "suite/accept-00.protos").exists())
            self.assertTrue(all(p.read_bytes() == b"unchanged original" for p in keep))
            self.assertEqual((work / "EXECUTION.json").read_bytes(), metadata)
            programs.write_artifacts(work, expected, manifest)
            self.assertEqual(programs.compare_artifacts(work, expected, manifest), [])

    def test_canonical_suite_helpers_are_not_generated_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work, manifest = self.layout(root)
            expected = self.artifacts()
            helpers = {}
            for name in sorted(programs.SUITE_AUXILIARIES):
                path = work.parent / name
                path.parent.mkdir(parents=True, exist_ok=True)
                content = b"// Original authored auxiliary SOURCE\\n"
                path.write_bytes(content)
                helpers[path] = content

            programs.write_artifacts(work, expected, manifest)
            self.assertEqual(programs.compare_artifacts(work, expected, manifest), [])
            registered = manifest.read_bytes()
            for name in programs.SUITE_AUXILIARIES:
                self.assertNotIn(name.encode("ascii"), registered)

            programs.cleanup(work, manifest)
            for path, content in helpers.items():
                self.assertEqual(path.read_bytes(), content)

            programs.write_artifacts(work, expected, manifest)
            self.assertEqual(programs.compare_artifacts(work, expected, manifest), [])

    def test_canonical_suite_helper_symlinks_are_rejected(self):
        for name in sorted(programs.SUITE_AUXILIARIES):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                work, manifest = self.layout(root)
                expected = self.artifacts()
                programs.write_artifacts(work, expected, manifest)

                target = root / "external-helper.protos"
                target.write_bytes(b"// Original external SOURCE")
                helper = work.parent / name
                helper.symlink_to(target)

                with self.assertRaisesRegex(ValueError, "symlink"):
                    programs.compare_artifacts(work, expected, manifest)

                with self.assertRaisesRegex(ValueError, "symlink"):
                    programs.write_artifacts(work, expected, manifest)

                self.assertEqual(target.read_bytes(), b"// Original external SOURCE")

    def test_conflicts_fail_before_writes_or_deletions(self):
        for suffix in (b"\twrong\t-\n", b"\\tsuite-native\\t-\n",
                       b""):
            with self.subTest(suffix=suffix), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                work, manifest = self.layout(root)
                prefix = b"library/xml/official/suite/accept-00.protos"
                row = prefix + (suffix or b"\tsuite-native\t-\n")
                manifest.write_bytes(manifest.read_bytes() + row + (row if not suffix else b""))
                before = self.snapshot(root)
                with self.assertRaisesRegex(ValueError, "conflicting"):
                    programs.write_artifacts(work, self.artifacts(), manifest)
                self.assertEqual(self.snapshot(root), before)

    def test_local_registration_rejects_auxiliary_and_duplicate_source_requests(self):
        for names in (["suite/Harness.protos"], ["suite/Fixtures.protos"], ["../Transfer.protos"],
                      ["suite/accept-00.protos", "suite/accept-00.protos"]):
            with self.subTest(names=names), self.assertRaises(ValueError):
                programs.local_manifest(b"# Original manifest\n", names)

    def test_unknown_registrations_and_unterminated_manifest_are_rejected(self):
        for extra in (b"library/xml/official/suite/Harness.protos\tsuite-native\t-\n", b"unfinished"):
            with self.subTest(extra=extra), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                work, manifest = self.layout(root)
                manifest.write_bytes(manifest.read_bytes() + extra)
                before = self.snapshot(root)
                with self.assertRaises(ValueError):
                    programs.write_artifacts(work, self.artifacts(), manifest)
                self.assertEqual(self.snapshot(root), before)

    def test_check_is_read_only_for_missing_modified_stale_and_unregistered_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work, manifest = self.layout(root)
            expected = self.artifacts()
            programs.write_artifacts(work, expected, manifest)
            (work.parent / "suite/accept-00.protos").write_bytes(b"modified")
            (work / "EXECUTION.json").unlink()
            (work.parent / "suite/stale.protos").write_bytes(b"unrelated")
            manifest.write_bytes(b"# No local rows\n")
            before = self.snapshot(root)
            problems = programs.compare_artifacts(work, expected, manifest)
            self.assertIn("modified artifact: suite/accept-00.protos", problems)
            self.assertIn("missing artifact: EXECUTION.json", problems)
            self.assertIn("unexpected suite source: suite/stale.protos", problems)
            self.assertIn("missing local XML registrations", problems)
            self.assertEqual(self.snapshot(root), before)

    def legacy(self, work):
        case = self.case()
        name = "programs/" + programs.sha256(case["identity"].encode()) + ".protos"
        sources = {name: b"// Original synthetic legacy entry\n"}
        support = []
        for path, canonical in programs.LEGACY_SUPPORT.items():
            sources[path] = b"// Original synthetic copied helper\n"
            support.append(dict(path=path, canonical_source=canonical, sha256=programs.sha256(sources[path])))
        for path, data in sources.items():
            target = work / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        (work / "EXECUTION.json").write_bytes(programs.json_bytes({
            "schema_version": 1, "cases": [dict(case, program_path=name, program_sha256=programs.sha256(sources[name]))],
            "support_sources": support}))
        return sources

    def test_migration_removes_only_verified_legacy_entries_and_copied_helpers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work, manifest = self.layout(root)
            sources = self.legacy(work)
            report = work / "reports/previous-smoke.json"
            report.parent.mkdir()
            report.write_bytes(b"historical evidence")
            programs.write_artifacts(work, self.artifacts(), manifest)
            self.assertTrue(all(not (work / name).exists() for name in sources))
            self.assertEqual(report.read_bytes(), b"historical evidence")
            self.assertEqual(programs.compare_artifacts(work, self.artifacts(), manifest), [])

    def test_cleanup_removes_legacy_sources_without_touching_metadata_or_user_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work, manifest = self.layout(root)
            sources = self.legacy(work)
            metadata = (work / "EXECUTION.json").read_bytes()
            original = manifest.read_bytes()
            user = work / "unrelated.protos"
            user.write_bytes(b"original user source")
            programs.cleanup(work, manifest)
            self.assertTrue(all(not (work / name).exists() for name in sources))
            self.assertEqual((work / "EXECUTION.json").read_bytes(), metadata)
            self.assertEqual(manifest.read_bytes(), original)
            self.assertEqual(user.read_bytes(), b"original user source")

    def test_legacy_modified_unknown_or_unproven_sources_prevent_all_mutation(self):
        for mode in ("modified", "unknown", "unproven", "support-modified"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                work, manifest = self.layout(root)
                sources = self.legacy(work)
                if mode == "modified":
                    (work / next(iter(sources))).write_bytes(b"changed")
                elif mode == "support-modified":
                    (work / next(iter(programs.LEGACY_SUPPORT))).write_bytes(b"changed helper")
                elif mode == "unknown":
                    (work / "programs/user.protos").write_bytes(b"user source")
                else:
                    metadata = json.loads((work / "EXECUTION.json").read_bytes())
                    metadata["cases"][0]["program_path"] = "programs/unproven.protos"
                    (work / "EXECUTION.json").write_bytes(programs.json_bytes(metadata))
                before = self.snapshot(root)
                for action in (lambda: programs.write_artifacts(work, self.artifacts(), manifest),
                               lambda: programs.cleanup(work, manifest)):
                    with self.assertRaises(ValueError):
                        action()
                    self.assertEqual(self.snapshot(root), before)

    def test_modified_suite_cleanup_and_symlink_targets_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work, manifest = self.layout(root)
            programs.write_artifacts(work, self.artifacts(), manifest)
            source = work.parent / "suite/accept-00.protos"
            source.write_bytes(b"user modification")
            before = self.snapshot(root)
            with self.assertRaisesRegex(ValueError, "modified"):
                programs.cleanup(work, manifest)
            self.assertEqual(self.snapshot(root), before)
            source.unlink()
            target = root / "user.protos"
            target.write_bytes(b"user source")
            source.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "symlink"):
                programs.write_artifacts(work, self.artifacts(), manifest)
            self.assertEqual(target.read_bytes(), b"user source")


class AcquisitionIndependenceTests(unittest.TestCase):
    def prepare_inputs(self, root):
        base = root / projection.OFFICIAL
        archive_path = base / projection.ZIP_PATH
        license_path = base / projection.LICENSE_PATH

        archive_path.parent.mkdir(parents=True)
        catalog = (
            "<TESTSUITE><TESTCASES>"
            + "".join(
                test_entry(identity="synthetic-%04d" % index)
                for index in range(2585)
            )
            + "</TESTCASES></TESTSUITE>"
        )

        with zipfile.ZipFile(archive_path, "w") as stream:
            stream.writestr(projection.CATALOG, catalog)
            stream.writestr("xmlconf/fixture.xml", "<r/>")

        license_bytes = b"Original synthetic license"
        license_path.write_bytes(license_bytes)

        for name in (
            "catalog-reconciliations.json",
            "classification-overrides.json",
        ):
            (base / name).write_text("{}", encoding="utf-8")

        suite = base / "suite"
        suite.mkdir()
        (suite / "Harness.protos").write_bytes(b"synthetic harness")
        (suite / "Fixtures.protos").write_bytes(b"synthetic fixtures")

        manifest = root / "protos/tests/conformance/manifest.tsv"
        manifest.write_bytes(b"# Original manifest\n")

        return (
            base,
            projection.digest(archive_path.read_bytes()),
            projection.digest(license_bytes),
        )

    def test_w3c_commands_are_independent_of_atom(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, zip_hash, license_hash = self.prepare_inputs(root)
            atom = base / "upstream/github-atom/announcements.atom"

            real_archive = projection.Archive
            real_inventory = projection.inventory
            real_construct = projection.construct_artifacts

            def inventory():
                return real_inventory(root=root)

            def construct(report, executable=False):
                return real_construct(
                    report, root=root, executable=executable
                )

            with patch.object(projection, "ZIP_SHA256", zip_hash), \
                    patch.object(projection, "LICENSE_SHA256", license_hash), \
                    patch.object(projection, "Archive",
                                 side_effect=lambda raw: real_archive(
                                     raw, expected=zip_hash)), \
                    patch.object(projection, "ROOT", root), \
                    patch.object(projection, "inventory",
                                 side_effect=inventory), \
                    patch.object(projection, "construct_artifacts",
                                 side_effect=construct), \
                    redirect_stdout(io.StringIO()), \
                    redirect_stderr(io.StringIO()):

                self.assertFalse(atom.exists())
                self.assertEqual(projection.main(["inventory"]), 0)
                self.assertEqual(projection.main(["generate"]), 0)
                self.assertEqual(projection.main(["check"]), 0)

                report_path = base / "work/INVENTORY.json"
                before = report_path.read_bytes()
                report = json.loads(before)

                self.assertNotIn("atom_sha256", report)
                self.assertEqual(report["license_sha256"], license_hash)
                self.assertEqual(report["logical_case_count"], 2585)
                self.assertEqual(
                    report["classification_counts"],
                    {"SUPPORTED_ACCEPT": 2585},
                )
                self.assertEqual(
                    report["executable_acceptance"], "NOT_EXECUTED"
                )

                atom.parent.mkdir(parents=True)
                atom.write_bytes(b"Unrelated changed Atom response")

                self.assertEqual(projection.main(["check"]), 0)
                self.assertEqual(projection.main(["generate"]), 0)
                self.assertEqual(projection.main(["check"]), 0)
                self.assertEqual(report_path.read_bytes(), before)
                self.assertEqual(
                    atom.read_bytes(),
                    b"Unrelated changed Atom response",
                )

    def test_inventory_rejects_modified_license(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, zip_hash, license_hash = self.prepare_inputs(root)
            (base / projection.LICENSE_PATH).write_bytes(b"modified")
            real_archive = projection.Archive

            with patch.object(projection, "ZIP_SHA256", zip_hash), \
                    patch.object(projection, "LICENSE_SHA256", license_hash), \
                    patch.object(projection, "Archive",
                                 side_effect=lambda raw: real_archive(
                                     raw, expected=zip_hash)):
                with self.assertRaisesRegex(
                    projection.ProjectionError,
                    "identity mismatch: .*LICENSE.html",
                ):
                    projection.inventory(root=root)

    def test_inventory_rejects_missing_license(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, zip_hash, license_hash = self.prepare_inputs(root)
            (base / projection.LICENSE_PATH).unlink()
            real_archive = projection.Archive

            with patch.object(projection, "ZIP_SHA256", zip_hash), \
                    patch.object(projection, "LICENSE_SHA256", license_hash), \
                    patch.object(projection, "Archive",
                                 side_effect=lambda raw: real_archive(
                                     raw, expected=zip_hash)):
                with self.assertRaisesRegex(
                    projection.ProjectionError,
                    "unavailable: .*LICENSE.html",
                ):
                    projection.inventory(root=root)


class CommandTests(unittest.TestCase):
    def test_check_command_is_read_only_and_reports_review_separately(self):
        report = {"inventory_status": "COMPLETE", "logical_case_count": 1,
                  "distinct_input_member_count": 1, "classification_counts": {"REQUIRES_REVIEW": 1},
                  "cases": [{"identity": "catalog.xml#review", "classification": "REQUIRES_REVIEW"}],
                  "inventory_failures": []}
        stdout, stderr = io.StringIO(), io.StringIO()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "protos/tests/conformance/manifest.tsv"
            manifest.parent.mkdir(parents=True)
            manifest.write_bytes(b"# Original manifest\n")
            with patch.object(projection, "ROOT", root), patch.object(projection, "inventory", return_value=report), \
                    patch.object(projection, "construct_artifacts", return_value={"EXECUTION.json": b"{}"}) as build, \
                    patch.object(projection, "write_artifacts") as write, redirect_stdout(stdout), redirect_stderr(stderr):
                self.assertEqual(projection.main(["check"]), 1)
                build.assert_called_once_with(report, executable=True)
                write.assert_not_called()
            self.assertEqual(manifest.read_bytes(), b"# Original manifest\n")
            self.assertFalse((root / projection.OFFICIAL).exists())
        self.assertIn("missing artifact: EXECUTION.json", stderr.getvalue())
        self.assertIn('"unresolved_classifications": ["catalog.xml#review"]', stdout.getvalue())

    def test_cleanup_does_not_require_acquisitions_or_construct_inventory(self):
        with patch.object(projection, "inventory") as inventory, \
                patch.object(projection, "construct_artifacts") as construct, \
                patch.object(projection, "cleanup") as cleanup:
            self.assertEqual(projection.main(["cleanup"]), 0)
            inventory.assert_not_called()
            construct.assert_not_called()
            cleanup.assert_called_once_with(projection.ROOT / projection.OFFICIAL / "work",
                                            projection.ROOT / "protos/tests/conformance/manifest.tsv")


if __name__ == "__main__":
    unittest.main()
