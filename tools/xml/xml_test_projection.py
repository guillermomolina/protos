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


"""Offline inventory of the pinned XML suite; no document parser is an oracle.

Only trusted catalog metadata may resolve archive-local external entities.
Fixture bytes are decoded strictly and lexically inspected, never parsed here.
Executable artifacts remain local; generation never executes Protos.
"""

import argparse
from collections import Counter
import codecs
import hashlib
import io
import json
from pathlib import Path
import posixpath
import re
import stat
import sys
from urllib.parse import urlsplit
from xml.parsers import expat
import zipfile

from xml_projection_programs import execution_artifacts, compare_artifacts, write_artifacts, cleanup

ROOT = Path(__file__).resolve().parents[2]
OFFICIAL = Path("protos/tests/conformance/library/xml/official")
ZIP_PATH = Path("upstream/w3c-20130923/xmlts20130923.zip")
LICENSE_PATH = Path("upstream/w3c-20130923/LICENSE.html")
ZIP_SHA256 = "f9510b3532926e1b4c2e54855b021e4b8a66ec98a5337dcf4ff07e8a41968deb"
LICENSE_SHA256 = "d4eedcb04dc16687b6d56b49ed06e1b55f599b8bd8e518af9773d1b7d2cf5c76"
CATALOG = "xmlconf/xmlconf.xml"
DTD = "xmlconf/testcases.dtd"
CATEGORIES = frozenset(("SUPPORTED_ACCEPT", "SUPPORTED_REJECT", "PROFILE_REJECT",
    "OUTSIDE_STRING_DOMAIN", "OUTSIDE_TRANSPORT_REQUIREMENT", "OUTSIDE_XML_EDITION", "OUTSIDE_VALIDATION_REQUIREMENT",
    "REQUIRES_REVIEW"))
META = ("ID", "URI", "TYPE", "RECOMMENDATION", "VERSION", "EDITION", "ENTITIES",
        "NAMESPACE", "SECTIONS", "OUTPUT", "OUTPUT3")
DEFAULTS = {"ENTITIES": "none", "RECOMMENDATION": "XML1.0", "NAMESPACE": "yes"}
ENUMS = {
    "TYPE": {"valid", "invalid", "not-wf", "error"},
    "ENTITIES": {"both", "none", "parameter", "general"},
    "NAMESPACE": {"yes", "no"},
    "RECOMMENDATION": {"XML1.0", "XML1.1", "NS1.0", "NS1.1", "XML1.0-errata2e",
                       "XML1.0-errata3e", "XML1.0-errata4e", "NS1.0-errata1e"},
}


class ReconciliationError(RuntimeError):
    """A recorded upstream repair is stale or cannot be verified."""


class ProjectionError(ValueError):
    """Invalid acquisition, unsafe metadata, or unreconciled inventory."""


def digest(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True) + "\n").encode("ascii")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ProjectionError("duplicate JSON key: " + key)
        result[key] = value
    return result


def pinned_read(path, expected, length=None):
    try:
        data = Path(path).read_bytes()
    except OSError as error:
        raise ProjectionError("required local input unavailable: %s: %s" % (path, error)) from error
    if digest(data) != expected or (length is not None and len(data) != length):
        raise ProjectionError("pinned acquisition identity mismatch: " + str(path))
    return data


def member_path(name):
    # ZIP separators are forward slashes. Reject Windows and URL interpretations.
    if not name or "\\" in name or "\x00" in name or name.startswith("/") or ":" in name:
        raise ProjectionError("unsafe archive member: " + repr(name))
    if ".." in name.split("/"):
        raise ProjectionError("archive parent traversal: " + name)
    normalized = posixpath.normpath(name)
    if normalized != "xmlconf" and not normalized.startswith("xmlconf/"):
        raise ProjectionError("archive member outside xmlconf: " + name)
    return normalized


def resolve(base, reference):
    """Resolve a catalog URI without network, filesystem or percent decoding."""
    uri = urlsplit(reference)
    if (not reference or uri.scheme or uri.netloc or uri.query or uri.fragment
            or reference.startswith("/") or "\\" in reference or "%" in reference
            or "\x00" in reference):
        raise ProjectionError("unsafe catalog reference: " + repr(reference))
    parent = base if base.endswith("/") else posixpath.dirname(base) + "/"
    path = posixpath.normpath(parent + reference)
    if path != "xmlconf" and not path.startswith("xmlconf/"):
        raise ProjectionError("catalog reference outside xmlconf: " + reference)
    return path + ("/" if reference.endswith("/") else "")


class Archive:
    """All members are examined only after the complete acquisition hash matches."""

    def __init__(self, data, expected=ZIP_SHA256):
        if digest(data) != expected:
            raise ProjectionError("pinned ZIP identity mismatch")
        self.sha256 = digest(data)
        self.zip = zipfile.ZipFile(io.BytesIO(data))
        self.members = {}
        for info in self.zip.infolist():
            name = member_path(info.filename)
            if name in self.members:
                raise ProjectionError("duplicate normalized member: " + name)
            mode = (info.external_attr >> 16) & 0xFFFF
            kind = stat.S_IFMT(mode)
            if kind not in (0, stat.S_IFREG, stat.S_IFDIR):
                raise ProjectionError("unsupported archive member type: " + name)
            if kind == stat.S_IFDIR and not info.is_dir():
                raise ProjectionError("inconsistent archive directory: " + name)
            if info.flag_bits & 1:
                raise ProjectionError("encrypted archive member: " + name)
            self.members[name] = info

    def read(self, name):
        info = self.members.get(name)
        if info is None or info.is_dir():
            raise ProjectionError("unresolved archive member: " + name)
        return self.zip.read(info)


def metadata(attrs):
    values = {key: attrs.get(key, DEFAULTS.get(key)) for key in META}
    for key in ("ID", "URI", "TYPE", "SECTIONS"):
        if not values[key]:
            raise ProjectionError("missing required TEST attribute: " + key)
    for key, allowed in ENUMS.items():
        if values[key] not in allowed:
            raise ProjectionError("unknown %s value: %s" % (key, values[key]))
    for key, allowed in (("VERSION", {"1.0", "1.1"}), ("EDITION", {"1", "2", "3", "4", "5"})):
        tokens = values[key].split() if values[key] is not None else None
        if tokens is not None and (not tokens or not set(tokens) <= allowed):
            raise ProjectionError("unknown %s list: %s" % (key, values[key]))
        values[key.lower() + "_list"] = tokens
    if values["EDITION"] is not None and values["VERSION"] is not None and len(values["version_list"]) != 1:
        raise ProjectionError("EDITION requires a single VERSION when VERSION is supplied")
    return values


class CatalogReader:
    """Expand only declared catalog entities from the verified in-memory ZIP.

    The XML element base stack persists across entity insertion. The Expat base
    separately identifies the physical entity for its relative system IDs.
    Only exact hash-bound reconciliation entries may repair upstream references.
    """

    def __init__(self, archive, reconciliations=None):
        self.archive = archive
        self.reconciliations = reconciliations or {}
        self.used_reconciliations = set()
        for identity, entry in self.reconciliations.items():
            required = {"identity", "archive_sha256", "catalog_member", "URI",
                        "effective_base", "original_target", "replacement_member",
                        "replacement_sha256", "reason"}
            if not required <= entry.keys() or identity != entry["identity"]:
                raise ProjectionError("incomplete reconciliation: " + identity)
            if entry["archive_sha256"] != archive.sha256:
                raise ProjectionError("reconciliation archive mismatch: " + identity)
        self.cases = []
        self.failures = []
        self.stack = []
        self.current = None
        self.declared = {DTD}
        self.active = []
        self.catalog_members = {}

    def failure(self, kind, **details):
        self.failures.append(dict(kind=kind, **details))

    def parse(self):
        parser = expat.ParserCreate()
        self.configure(parser, CATALOG)
        self.parse_member(parser, CATALOG)
        identities = Counter(case["identity"] for case in self.cases)
        ids = Counter(case["ID"] for case in self.cases)
        for case in self.cases:
            if identities[case["identity"]] > 1 or ids[case["ID"]] > 1:
                case["inventory_problem"] = "duplicate catalog identity or ID"
                self.failure("DUPLICATE_IDENTITY", identity=case["identity"], id=case["ID"])
        unused = self.reconciliations.keys() - self.used_reconciliations
        if unused:
            raise ProjectionError("unused reconciliation: " + ", ".join(sorted(unused)))
        return self.cases

    def reconciled_target(self, case, target):
        entry = self.reconciliations.get(case["identity"])
        if entry is None:
            return target
        identity = case["identity"]
        if identity in self.used_reconciliations:
            raise ProjectionError("duplicate reconciliation use: " + identity)
        for field in ("catalog_member", "URI", "effective_base"):
            if case[field] != entry[field]:
                raise ProjectionError("stale reconciliation " + field + ": " + identity)
        if target != entry["original_target"] or target in self.archive.members:
            raise ProjectionError("stale reconciliation original target: " + identity)
        replacement = member_path(entry["replacement_member"])
        if digest(self.archive.read(replacement)) != entry["replacement_sha256"]:
            raise ProjectionError("reconciliation replacement hash mismatch: " + identity)
        self.used_reconciliations.add(identity)
        case["reconciliation"] = entry
        return replacement

    def parse_member(self, parser, member):
        if member in self.active:
            raise ProjectionError("recursive catalog entity: " + member)
        raw = self.archive.read(member)
        self.catalog_members[member] = digest(raw)
        self.active.append(member)
        try:
            parser.Parse(raw, True)
        finally:
            self.active.pop()

    def configure(self, parser, member):
        parser.SetBase(member)
        parser.SetParamEntityParsing(expat.XML_PARAM_ENTITY_PARSING_ALWAYS)
        parser.StartElementHandler = lambda name, attrs: self.start(member, name, attrs)
        parser.EndElementHandler = self.end
        parser.CharacterDataHandler = self.text
        parser.EntityDeclHandler = self.entity_decl
        parser.ExternalEntityRefHandler = lambda context, base, system, public: self.external(
            parser, context, base, system, public)
        parser.SkippedEntityHandler = lambda name, parameter: self.failure(
            "UNRESOLVED_ENTITY", name=name, parameter=parameter)

    def entity_decl(self, name, parameter, value, base, system, public, notation):
        if system is None:
            return
        if public is not None or notation is not None or parameter:
            raise ProjectionError("unexpected catalog entity declaration: " + name)
        target = resolve(base, system)
        if not target.endswith(".xml"):
            raise ProjectionError("unexpected catalog entity destination: " + target)
        self.declared.add(target)

    def external(self, parent, context, base, system, public):
        if public is not None:
            raise ProjectionError("public catalog entities are not authorized")
        target = resolve(base, system)
        if target not in self.declared:
            raise ProjectionError("undeclared catalog entity destination: " + target)
        if target not in self.archive.members:
            self.failure("UNRESOLVED_CATALOG_REFERENCE", source=base, reference=system, target=target)
            return 1
        child = parent.ExternalEntityParserCreate(context)
        self.configure(child, target)
        self.parse_member(child, target)
        return 1

    def start(self, member, name, attrs):
        if name not in ("TESTSUITE", "TESTCASES", "TEST", "EM", "B"):
            raise ProjectionError("unexpected catalog element: " + name)
        inherited = self.stack[-1][1] if self.stack else CATALOG
        effective = resolve(inherited, attrs["xml:base"]) if "xml:base" in attrs else inherited
        ancestry = [entry[2] for entry in self.stack if entry[2] is not None]
        self.stack.append((name, effective, attrs.get("PROFILE")))
        if name == "TEST":
            if self.current is not None:
                raise ProjectionError("nested TEST")
            case = metadata(attrs)
            case.update(identity=member + "#" + case["ID"], catalog_member=member,
                        ancestry=ancestry, effective_base=effective, description="",
                        input_member=None, input_archive_member=None, input_sha256=None, outputs={},
                        original_input_target=None, reconciliation=None)
            try:
                target = resolve(effective, case["URI"])
                case["original_input_target"] = target
                try:
                    target = self.reconciled_target(case, target)
                except ProjectionError as error:
                    raise ReconciliationError(str(error)) from error
                case["input_member"] = target
                raw = self.archive.read(target)
                case["input_sha256"] = digest(raw)
                case["input_archive_member"] = self.archive.members[target].filename
            except ProjectionError as error:
                case["inventory_problem"] = str(error)
                self.failure("UNRESOLVED_CASE_REFERENCE", identity=case["identity"], message=str(error))
            for field in ("OUTPUT", "OUTPUT3"):
                if case[field] is not None:
                    try:
                        target = resolve(effective, case[field])
                        case["outputs"][field] = {"member": target, "archive_member": self.archive.members[target].filename if target in self.archive.members else target, "sha256": digest(self.archive.read(target))}
                    except ProjectionError as error:
                        self.failure("UNRESOLVED_OUTPUT_REFERENCE", identity=case["identity"], field=field, message=str(error))
            self.current = case
            self.cases.append(case)

    def end(self, name):
        if self.stack.pop()[0] != name:
            raise ProjectionError("catalog stack mismatch")
        if name == "TEST":
            self.current = None

    def text(self, value):
        if self.current is not None:
            self.current["description"] += value


def decode_fixture(raw):
    """Explicit strict transport decoding, consuming at most one matching BOM.

    This is acquisition tooling only. XML declaration encoding syntax is still
    tested by Protos; ambiguous transport metadata never becomes replacement text.
    """
    encoding, skip = "utf-8", 0
    for bom, codec in ((b"\x00\x00\xfe\xff", "utf-32-be"), (b"\xff\xfe\x00\x00", "utf-32-le"),
                       (b"\xef\xbb\xbf", "utf-8"), (b"\xfe\xff", "utf-16-be"), (b"\xff\xfe", "utf-16-le")):
        if raw.startswith(bom):
            encoding, skip = codec, len(bom)
            break
    if not skip:
        signatures = {b"\x00<\x00?": "utf-16-be", b"<\x00?\x00": "utf-16-le",
                      b"\x00\x00\x00<": "utf-32-be", b"<\x00\x00\x00": "utf-32-le"}
        if raw[:4] in signatures:
            encoding = signatures[raw[:4]]
        elif raw.startswith(b"\x4c\x6f\xa7\x94"):
            return {"status": "review", "reason": "EBCDIC transport requires explicit adjudication"}, None
        else:
            decl = re.match(br"<\?xml\s+[^?]*\?>", raw)
            if decl:
                match = re.search(br"\bencoding\s*=\s*(['\"])([A-Za-z][A-Za-z0-9._-]*)\1", decl[0])
                if match:
                    encoding = match[2].decode("ascii")
    try:
        codec = codecs.lookup(encoding).name
    except LookupError:
        return {"status": "review", "encoding": encoding, "reason": "unknown fixture encoding"}, None
    allowed = {"utf-8", "utf-16", "utf-16-be", "utf-16-le", "utf-32", "utf-32-be", "utf-32-le",
               "ascii", "iso8859-1", "iso8859-2", "iso8859-3", "iso8859-4", "iso8859-5",
               "iso8859-6", "iso8859-7", "iso8859-8", "iso8859-9", "iso8859-15",
               "shift_jis", "euc_jp", "iso2022_jp", "cp1252", "big5", "gb2312", "euc_kr"}
    if codec not in allowed:
        return {"status": "review", "encoding": encoding, "reason": "fixture codec not explicitly supported"}, None
    if codec in ("utf-16", "utf-32") and not skip:
        return {"status": "review", "encoding": encoding, "reason": "ambiguous byte order"}, None
    try:
        text = raw[skip:].decode(encoding, errors="strict")
    except (UnicodeError, LookupError):
        return {"status": "outside", "encoding": encoding, "transport_bom_consumed": bool(skip),
                "reason": "strict decoding cannot produce a semantic String"}, None
    if any(0xD800 <= ord(c) <= 0xDFFF for c in text):
        return {"status": "outside", "encoding": encoding, "reason": "unpaired surrogate"}, None
    decl = re.match(r"<\?xml\s+[^?]*\?>", text)
    mismatch = False
    if decl:
        match = re.search(r"\bencoding\s*=\s*(['\"])([A-Za-z][A-Za-z0-9._-]*)\1", decl[0])
        if match:
            try:
                stated = codecs.lookup(match[2]).name
            except LookupError:
                return {"status": "review", "encoding": encoding, "reason": "unknown declared encoding"}, None
            mismatch = stated != codec and not (stated == "utf-16" and codec.startswith("utf-16-")) and not (
                stated == "utf-32" and codec.startswith("utf-32-"))
    return {"status": "decoded", "encoding": encoding, "transport_bom_consumed": bool(skip),
            "declaration_transport_mismatch": mismatch}, text


def lexical_features(text):
    """Find real declarations outside comments, CDATA, PI and quoted tags.

    This scanner establishes profile features, not XML well-formedness. It
    intentionally does not process a DTD or resolve entity references.
    """
    index = 0
    while index < len(text):
        if text[index] != "<":
            index += 1
            continue
        skipped = False
        for opening, closing in (("<!--", "-->"), ("<![CDATA[", "]]>"), ("<?", "?>")):
            if text.startswith(opening, index):
                end = text.find(closing, index + len(opening))
                if end < 0:
                    return {"doctype": False}
                index = end + len(closing)
                skipped = True
                break
        if skipped:
            continue
        if text.startswith("<!DOCTYPE", index):
            return {"doctype": True, "doctype_offset": index}
        index += 1
        quote = None
        while index < len(text):
            c = text[index]
            index += 1
            if quote is not None:
                if c == quote:
                    quote = None
            elif c in "\"'":
                quote = c
            elif c == ">":
                break
    return {"doctype": False}


def historical_name_production(sections):
    """Interpret bracketed production numbers, never a section substring.

    Repertoire changes affect NameStartChar [4], NameChar [4a] and Name [5].
    Char and AttValue negative cases do not acquire this ambiguity.
    """
    return bool(set(re.findall(r"\[([0-9]+[a-z]?)\]", sections)) & {"4", "4a", "5"})


def classify(case, decoding, text):
    def result(category, reason, reference, expected=None):
        return dict(classification=category, reason=reason, reference=reference, expected=expected)
    contract = "spec/semantics/XML.md §§1, 5, 6"
    if case.get("inventory_problem"):
        return result("REQUIRES_REVIEW", case["inventory_problem"], "XML suite catalog DTD")
    if decoding["status"] == "outside":
        return result("OUTSIDE_STRING_DOMAIN", decoding["reason"], contract)
    if decoding["status"] != "decoded":
        return result("REQUIRES_REVIEW", decoding["reason"], contract)
    versions, editions = case["version_list"], case["edition_list"]
    if (case["RECOMMENDATION"] in ("XML1.1", "NS1.1") or
            (versions is not None and "1.0" not in versions)):
        return result("OUTSIDE_XML_EDITION", "Catalog requirement does not apply to selected XML/Namespaces edition", contract)
    if editions is not None:
        if case["RECOMMENDATION"].startswith("NS"):
            return result("REQUIRES_REVIEW", "Explicit namespace-case edition needs reconciliation with the catalog XML-edition convention", "xmlconf/testcases.dtd EDITION contract; Namespaces 1.0 Third Edition")
        if "5" not in editions:
            return result("OUTSIDE_XML_EDITION", "Catalog excludes XML 1.0 Fifth Edition", contract)
    if lexical_features(text)["doctype"]:
        return result("PROFILE_REJECT", "Real DOCTYPE is outside the accepted DTD-free profile; do not process its subset", contract, "reject")
    if decoding.get("declaration_transport_mismatch"):
        return result("REQUIRES_REVIEW", "Transport/declaration inconsistency is not a semantic String defect; adjudicate remaining document", contract)
    if case["ENTITIES"] != "none":
        return result("REQUIRES_REVIEW", "External-entity-dependent expectation without a lexically established DOCTYPE", contract)
    declaration = re.match(r"<\?xml\s+version\s*=\s*(['\"])([^'\"]+)\1", text)
    if declaration and declaration[2] != "1.0":
        return result("PROFILE_REJECT", "Protos admits only declaration version 1.0", "spec/semantics/XML.md §§1, 3", "reject")
    if case["NAMESPACE"] == "no":
        return result("REQUIRES_REVIEW", "Upstream disabled namespaces; Protos namespace processing is mandatory", "spec/semantics/XML.md §4")
    kind = case["TYPE"]
    if kind == "error":
        return result("REQUIRES_REVIEW", "Upstream error reporting is optional; requires individual adjudication", "xmlconf/testcases.dtd TYPE contract")
    if kind == "invalid" and case["RECOMMENDATION"].startswith("NS"):
        return result("REQUIRES_REVIEW", "Namespace-specific invalid case needs validity/namespace reconciliation", "Namespaces 1.0 Third Edition; spec/semantics/XML.md §4")
    # Older name-production negative cases can change expectation in Fifth Edition.
    if kind == "not-wf" and historical_name_production(case["SECTIONS"]) and editions is None:
        return result("REQUIRES_REVIEW", "Unqualified historical name-production failure needs Fifth Edition reconciliation", "XML 1.0 Fifth Edition §2.3")
    if kind in ("valid", "invalid"):
        return result("SUPPORTED_ACCEPT", "DTD-free valid or well-formed validity-only invalid input; nonvalidating acceptance", "xmlconf/testcases.dtd TYPE contract; " + contract, "accept")
    return result("SUPPORTED_REJECT", "Applicable namespace-aware not-well-formed catalog case", "XML 1.0 Fifth Edition; Namespaces 1.0 Third Edition; " + contract, "reject")


def apply_overrides(cases, overrides):
    by_identity = {case["identity"]: case for case in cases}
    required = {"identity", "input_member", "input_sha256", "previous_classification",
                "final_classification", "expected", "normative_reason", "reference"}
    for identity, override in overrides.items():
        if not required <= override.keys() or identity != override["identity"]:
            raise ProjectionError("incomplete override identity: " + identity)
        if identity not in by_identity:
            raise ProjectionError("override refers to absent case: " + identity)
        case = by_identity[identity]
        if case.get("inventory_problem"):
            raise ProjectionError("override cannot conceal inventory failure: " + identity)
        for field in ("input_member", "input_sha256"):
            if case[field] != override[field]:
                raise ProjectionError("override input identity drift: " + identity)
        if case["classification"] != override["previous_classification"]:
            raise ProjectionError("override mechanical classification drift: " + identity)
        final, expected = override["final_classification"], override["expected"]
        if final not in CATEGORIES or expected not in ("accept", "reject", None):
            raise ProjectionError("invalid override outcome: " + identity)
        if (final == "SUPPORTED_ACCEPT" and expected != "accept") or (
                final in ("SUPPORTED_REJECT", "PROFILE_REJECT") and expected != "reject") or (
                final not in ("SUPPORTED_ACCEPT", "SUPPORTED_REJECT", "PROFILE_REJECT") and expected is not None):
            raise ProjectionError("contradictory override outcome: " + identity)
        if not override["normative_reason"] or not override["reference"]:
            raise ProjectionError("override lacks normative justification: " + identity)
        case.update(classification=final, expected=expected, reason=override["normative_reason"],
                    reference=override["reference"], override=override)


def inventory(root=ROOT):
    base = Path(root) / OFFICIAL
    archive = Archive(pinned_read(base / ZIP_PATH, ZIP_SHA256))
    pinned_read(base / LICENSE_PATH, LICENSE_SHA256)
    reconciliations = json.loads((base / "catalog-reconciliations.json").read_text(encoding="utf-8"),
                                object_pairs_hook=unique_object)
    reader = CatalogReader(archive, reconciliations)
    cases = reader.parse()
    if len(cases) != 2585:
        raise ProjectionError("pinned catalog logical case count differs from 2585")
    for case in cases:
        decoding, text = ({"status": "review", "reason": "unresolved input"}, None)
        if case["input_sha256"] is not None:
            decoding, text = decode_fixture(archive.read(case["input_member"]))
        case["decoding"] = decoding
        case.update(classify(case, decoding, text))
        case["upstream_nonvalidating_expected"] = {
            "valid": "accept", "invalid": "accept", "not-wf": "reject", "error": "optional"
        }[case["TYPE"]]
        case["profile_expectation_changed"] = (
            case["expected"] is not None and case["expected"] != case["upstream_nonvalidating_expected"]
        )
    overrides = json.loads((base / "classification-overrides.json").read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    apply_overrides(cases, overrides)
    for case in cases:
        case["profile_expectation_changed"] = (
            case["expected"] is not None and case["expected"] != case["upstream_nonvalidating_expected"]
        )
    report = {
        "schema_version": 2, "zip_sha256": ZIP_SHA256, "license_sha256": LICENSE_SHA256,
        "catalog_members": reader.catalog_members, "cases": cases,
        "logical_case_count": len(cases),
        "distinct_input_member_count": len({c["input_member"] for c in cases if c["input_sha256"]}),
        "classification_counts": dict(sorted(Counter(c["classification"] for c in cases).items())),
        "inventory_failures": reader.failures,
        "inventory_status": "FAIL" if reader.failures else "COMPLETE",
        "executable_acceptance": "NOT_EXECUTED",
        "complete_acceptance_claim_permitted": False,
    }
    return report


def artifacts(report):
    # Every cell is a JSON value, avoiding physical tabs/newlines and preserving
    # null, Unicode scalars and contributor descriptions without normalization.
    columns = ("identity",) + META + ("ancestry", "effective_base", "original_input_target", "reconciliation", "input_member", "input_archive_member", "input_sha256",
        "description", "decoding", "classification", "reason", "reference", "expected", "outputs")
    tsv = ["\t".join(columns)]
    for case in report["cases"]:
        tsv.append("\t".join(json.dumps(case.get(key), ensure_ascii=True, sort_keys=True) for key in columns))
    hashes = dict(report["catalog_members"])
    for case in report["cases"]:
        if case["input_sha256"]:
            hashes[case["input_member"]] = case["input_sha256"]
        for output in case["outputs"].values():
            hashes[output["member"]] = output["sha256"]
    return {"INVENTORY.json": json_bytes(report), "CASES.tsv": ("\n".join(tsv) + "\n").encode("ascii"),
            "SHA256SUMS": "".join(sha + "  " + name + "\n" for name, sha in sorted(hashes.items())).encode("utf-8")}


def construct_artifacts(report, root=ROOT, executable=False):
    """Shared deterministic path for generate and read-only check."""
    result = artifacts(report)
    if executable:
        if report["inventory_failures"]:
            raise ProjectionError("cannot generate with unresolved inventory references or identities")
        base = Path(root) / OFFICIAL
        archive = Archive(pinned_read(base / ZIP_PATH, ZIP_SHA256))
        def text_for(case):
            decoding, text = decode_fixture(archive.read(case["input_member"]))
            if decoding != case["decoding"] or text is None:
                raise ProjectionError("executable case has no exact semantic String: " + case["identity"])
            return text
        result.update(execution_artifacts(report, text_for,
                                          harness=(base / "suite/Harness.protos").read_bytes(),
                                          fixtures=(base / "suite/Fixtures.protos").read_bytes()))
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("inventory", "generate", "check", "cleanup"))
    args = parser.parse_args(argv)
    try:
        work = ROOT / OFFICIAL / "work"
        manifest = ROOT / "protos/tests/conformance/manifest.tsv"
        if args.command == "cleanup":
            cleanup(work, manifest)
            return 0
        report = inventory()
        expected = construct_artifacts(report, executable=args.command != "inventory")
        problems = []
        if args.command == "check":
            problems = compare_artifacts(work, expected, manifest=manifest)
            for problem in problems:
                print(problem, file=sys.stderr)
        else:
            write_artifacts(work, expected,
                            manifest=manifest if args.command == "generate" else None)
        print(json.dumps({key: report[key] for key in ("inventory_status", "logical_case_count",
              "distinct_input_member_count", "classification_counts")}, sort_keys=True))
        reviews = [c["identity"] for c in report["cases"] if c["classification"] == "REQUIRES_REVIEW"]
        print(json.dumps({"unresolved_classifications": reviews,
                          "executable_acceptance": "NOT_EXECUTED",
                          "complete_acceptance_claim_permitted": False}, sort_keys=True))
        if problems:
            return 1
        if report["inventory_failures"]:
            print("Inventory has unresolved references/identities; inspect work/INVENTORY.json", file=sys.stderr)
            return 1
    except (ProjectionError, ReconciliationError, ValueError, OSError, zipfile.BadZipFile, expat.ExpatError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
