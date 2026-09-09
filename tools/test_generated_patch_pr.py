#!/usr/bin/env python3
import importlib.util
import pathlib
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent
TARGET = HERE / "generated_patch_pr.py"
spec = importlib.util.spec_from_file_location("generated_patch_pr", TARGET)
m = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = m
spec.loader.exec_module(m)

class PureTests(unittest.TestCase):
    def test_branch(self):
        self.assertEqual(
            m.branch_name("GITHUB002-D", "g002d-7c3f54a91e2b"),
            "protos-patch/github002-d/g002d-7c3f54a91e2b")

    def test_bad_slice(self):
        with self.assertRaises(m.PatchPrError):
            m.branch_name("github002-d", "g002d-7c3f54a91e2b")

    def test_bad_artifact(self):
        with self.assertRaises(m.PatchPrError):
            m.branch_name("GITHUB002-D", "../../main")

    def test_body_marker_near_top(self):
        marker = m.body_marker("GITHUB002-D", "g002d-7c3f54a91e2b")
        self.assertTrue(m.body_owned(marker + "\nCloses #152",
                                     "GITHUB002-D", "g002d-7c3f54a91e2b"))
        self.assertFalse(m.body_owned("\n"*6 + marker,
                                      "GITHUB002-D", "g002d-7c3f54a91e2b"))

    def test_commit_trailers(self):
        msg = "Subject\n\n" + m.commit_trailers(
            "GITHUB002-D", "g002d-7c3f54a91e2b")
        self.assertTrue(m.commit_owned(
            msg, "GITHUB002-D", "g002d-7c3f54a91e2b"))

if __name__ == "__main__":
    unittest.main()
