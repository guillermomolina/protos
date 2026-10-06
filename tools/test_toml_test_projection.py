#!/usr/bin/env python3
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

"""Self-test for tools/toml_test_projection.py (offline, repository copy)."""

from __future__ import print_function

import re
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import toml_test_projection as projection  # noqa: E402


REPO_ROOT = projection.REPO_ROOT
COPIED = (projection.OFFICIAL_DIR, projection.MANIFEST_PATH)
ESCAPE = re.compile(r'\\(u\{([0-9A-F]{1,6})\}|.)')
SIMPLE_ESCAPES = {"\\": "\\", '"': '"', "n": "\n", "r": "\r", "t": "\t"}


def decode_literal(literal):
    """Decode one double-quoted Protos literal produced by the projection."""
    assert literal.startswith('"') and literal.endswith('"'), literal
    body = literal[1:-1]

    def replace(match):
        if match.group(2):
            return chr(int(match.group(2), 16))
        return SIMPLE_ESCAPES[match.group(1)]

    unescaped = ESCAPE.sub(replace, body)
    assert '"' not in ESCAPE.sub("", body), literal
    return unescaped


class ProjectionTest(unittest.TestCase):

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for relative in COPIED:
            source = REPO_ROOT / relative
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(str(source), str(target))
            else:
                shutil.copyfile(str(source), str(target))
        self.upstream = self.root / projection.UPSTREAM_DIR
        self.suite = self.root / projection.SUITE_DIR

    def tearDown(self):
        self.temp.cleanup()

    def assertCheckFails(self, fragment):
        with self.assertRaises(projection.ProjectionError) as caught:
            projection.check(self.root)
        self.assertIn(fragment, str(caught.exception))

    def test_repository_projection_is_current(self):
        counts = projection.check(self.root)
        self.assertEqual(counts, projection.EXPECTED_COUNTS)
        self.assertEqual(
            counts["executable-count"] + counts["not-applicable-count"],
            counts["logical-case-count"])

    def test_exact_case_inventory(self):
        _, cases, _ = projection.derive(projection.Snapshot(self.root))
        by_direction = {}
        for case in cases:
            by_direction.setdefault(
                (case.direction, case.classification), []).append(case)
        self.assertEqual(len(by_direction[("decoder-valid", "EXECUTABLE")]), 214)
        self.assertEqual(len(by_direction[("encoder", "EXECUTABLE")]), 214)
        self.assertEqual(len(by_direction[("decoder-invalid", "EXECUTABLE")]), 456)
        not_applicable = by_direction[
            ("decoder-invalid", projection.NOT_APPLICABLE)]
        self.assertEqual(
            tuple(case.name for case in not_applicable),
            projection.NOT_APPLICABLE_CASES)
        self.assertTrue(all(case.test_name is None for case in not_applicable))
        for name in ("invalid/encoding/ideographic-space",
                     "invalid/encoding/utf16-comment",
                     "invalid/encoding/utf16-key"):
            self.assertIn(name, [
                case.test_name for case in
                by_direction[("decoder-invalid", "EXECUTABLE")]])

    def test_generation_is_deterministic(self):
        first = projection.derive(projection.Snapshot(self.root))[2]
        second = projection.derive(projection.Snapshot(self.root))[2]
        self.assertEqual(first, second)

    def test_generate_repairs_stale_derived_files(self):
        shard = self.suite / "valid-bool.protos"
        shard.write_bytes(shard.read_bytes() + b"// edited\n")
        (self.suite / "valid-extra.protos").write_bytes(b"tests: []\n")
        self.assertCheckFails("stale")
        projection.generate(self.root)
        projection.check(self.root)

    def test_changed_fixture_fails(self):
        fixture = self.upstream / "tests/valid/bool/bool.toml"
        # Same length, so only the content digest can detect the change.
        fixture.write_bytes(fixture.read_bytes().replace(b"f = false", b"g = false"))
        self.assertCheckFails("snapshot digest")

    def test_regenerated_checksums_cannot_absorb_fixture_change(self):
        fixture = self.upstream / "tests/valid/bool/bool.json"
        fixture.write_bytes(fixture.read_bytes().replace(b'"f":', b'"g":'))
        projection.generate(self.root)
        self.assertCheckFails("snapshot digest")

    def test_missing_fixture_fails(self):
        (self.upstream / "tests/invalid/bool/wrong-case-true.toml").unlink()
        self.assertCheckFails("differs from selector")

    def test_extra_fixture_fails(self):
        (self.upstream / "tests/valid/bool/extra.toml").write_bytes(b"")
        self.assertCheckFails("differs from selector")

    def test_changed_selector_fails(self):
        selector = self.upstream / projection.SELECTOR_NAME
        selector.write_bytes(selector.read_bytes() + b"valid/x.toml\n")
        self.assertCheckFails("pinned blob")

    def test_changed_license_fails(self):
        license_path = self.upstream / "LICENSE"
        license_path.write_bytes(license_path.read_bytes() + b"\n")
        self.assertCheckFails("pinned blob")

    def test_not_applicable_set_is_mechanical_and_pinned(self):
        fixture = self.upstream / "tests/invalid/encoding/utf16-bom.toml"
        fixture.write_bytes(b"# now valid UTF-8\n")
        self.assertCheckFails("not-applicable set")

    def test_stale_shard_fails(self):
        shard = self.suite / "invalid-key.protos"
        shard.write_bytes(shard.read_bytes().replace(b"Harness.rejects", b"Harness.decodes", 1))
        self.assertCheckFails("stale")

    def test_unexpected_suite_file_fails(self):
        (self.suite / "notes.txt").write_bytes(b"")
        self.assertCheckFails("suite directory content")

    def test_missing_harness_fails(self):
        (self.suite / projection.HARNESS_NAME).unlink()
        self.assertCheckFails("suite directory content")

    def test_unexpected_upstream_file_fails(self):
        (self.upstream / "README.md").write_bytes(b"")
        self.assertCheckFails("upstream directory content")

    def test_manifest_must_list_every_shard(self):
        manifest = self.root / projection.MANIFEST_PATH
        text = manifest.read_text(encoding="utf-8")
        line = projection.MANIFEST_PREFIX + "valid-key.protos\tsuite-native\t-\n"
        self.assertIn(line, text)
        manifest.write_text(text.replace(line, ""), encoding="utf-8")
        self.assertCheckFails("manifest")

    def test_literals_round_trip_every_fixture(self):
        snapshot = projection.Snapshot(self.root)
        for name, data in snapshot.fixtures.items():
            text = projection.strict_utf8(data)
            if text is None:
                continue
            pieces = projection.protos_string_pieces(text)
            self.assertEqual("".join(decode_literal(p) for p in pieces), text, name)
            for piece in pieces:
                self.assertNotIn("\n", piece)
                self.assertNotIn("\r", piece)
                self.assertTrue(all(0x20 <= ord(c) < 0x7F for c in piece), name)

    def test_escape_forms(self):
        self.assertEqual(projection.protos_escape("\0"), "\\u{0}")
        self.assertEqual(projection.protos_escape("\x7f"), "\\u{7F}")
        self.assertEqual(projection.protos_escape("\u3000"), "\\u{3000}")
        self.assertEqual(projection.protos_escape("\U0001F600"), "\\u{1F600}")
        self.assertEqual(projection.protos_escape("\b"), "\\u{8}")
        self.assertEqual(projection.protos_escape('"'), '\\"')
        self.assertEqual(projection.protos_escape("\\"), "\\\\")
        self.assertEqual(projection.protos_string_pieces(""), ['""'])

    def test_generated_test_names_preserve_upstream_identity(self):
        _, cases, outputs = projection.derive(projection.Snapshot(self.root))
        names = []
        for path, data in outputs.items():
            if path.startswith(projection.SUITE_DIR + "/"):
                names.extend(re.findall(r'\n    Test\("([^"]*)"', data.decode("utf-8")))
        self.assertEqual(len(names), 884)
        self.assertEqual(len(set(names)), 884)
        expected = sorted(case.test_name for case in cases if case.test_name)
        self.assertEqual(sorted(names), expected)
        for case in cases:
            stem = case.fixtures[0].rsplit(".", 1)[0]
            if case.direction == "encoder":
                self.assertEqual(case.name, "encoder/" + stem[len("valid/"):])
            else:
                self.assertEqual(case.name, stem)


if __name__ == "__main__":
    unittest.main()
