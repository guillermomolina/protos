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

"""Reconcile formal GitHub Issue intake facts: family and native parent."""

from __future__ import print_function

import argparse
import json
import os
import re
import sys
import time
from urllib import error, parse, request


API_VERSION = "2026-03-10"

FORMAL_FAMILIES = (
    "GITHUB",
    "PLAT",
    "TOOL",
    "PERF",
    "DIST",
    "BUG",
    "LIB",
    "CLI",
    "DOC",
    "AUD",
    "LM",
    "I",
    "D",
    "B",
)

FAMILY_LABEL_PREFIX = "family:"
TRUSTED_AUTHOR_ASSOCIATIONS = frozenset(("OWNER", "MEMBER", "COLLABORATOR"))

FAMILY_PATTERN = "|".join(
    re.escape(name) for name in sorted(FORMAL_FAMILIES, key=len, reverse=True)
)
TITLE_IDENTIFIER_RE = re.compile(
    r"^(?P<identifier>(?P<family>%s)\d{3}(?:-[A-Z0-9]+)*)(?=\s|—|$)"
    % FAMILY_PATTERN
)
STRICT_WORK_ITEM_RE = re.compile(
    r"^(?P<identifier>(?P<family>%s)\d{3}(?:-[A-Z0-9]+)*)"
    r"\s+—\s+\S.*$"
    % FAMILY_PATTERN
)

PARENT_LINE_RE = re.compile(
    r"(?im)^\s*(?:[-*]\s*)?(?:\*\*)?"
    r"Parent(?:\s+work\s+item|\s+issue)?"
    r"(?:\*\*)?\s*:\s*[^\n#]*#(\d+)\b"
)

SECTION_RE = re.compile(
    r"(?ms)^### ([^\n]+)\n+(.*?)(?=^### |\Z)"
)


class IntakeError(Exception):
    pass


class ApiError(IntakeError):
    def __init__(self, status, method, url, detail):
        super(ApiError, self).__init__(
            "%s %s failed: HTTP %s: %s"
            % (method, url, status, detail[:1000])
        )
        self.status = status
        self.method = method
        self.url = url
        self.detail = detail


def _json_request(url, token, method="GET", payload=None, allow_404=False):
    data = None
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + token,
        "User-Agent": "protos-issue-intake",
        "X-GitHub-Api-Version": API_VERSION,
    }
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = request.Request(url, data=data, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=30) as response:
            raw = response.read().decode("utf-8")
            return (json.loads(raw) if raw else None), response.status
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")
        if allow_404 and exc.code == 404:
            return None, 404
        raise ApiError(exc.code, method, url, detail)
    except error.URLError as exc:
        raise IntakeError("%s %s failed: %s" % (method, url, exc))


class GitHubApi(object):
    def __init__(self, repository, token):
        self.repository = repository
        self.token = token

    def _url(self, path):
        return "https://api.github.com/repos/%s%s" % (self.repository, path)

    def fetch_issue(self, number):
        data, _ = _json_request(
            self._url("/issues/%d" % int(number)),
            self.token,
        )
        if data.get("pull_request"):
            raise IntakeError("#%d resolves to a pull request, not an Issue" % int(number))
        return data

    def list_open_issues(self):
        result = []
        page = 1
        while True:
            data, _ = _json_request(
                self._url("/issues?state=open&per_page=100&page=%d" % page),
                self.token,
            )
            if not data:
                break
            for issue in data:
                if not issue.get("pull_request"):
                    result.append(issue)
            if len(data) < 100:
                break
            page += 1
        return result

    def add_labels(self, number, names):
        if not names:
            return
        _json_request(
            self._url("/issues/%d/labels" % int(number)),
            self.token,
            method="POST",
            payload={"labels": list(names)},
        )

    def remove_label(self, number, name):
        encoded = parse.quote(name, safe="")
        _json_request(
            self._url("/issues/%d/labels/%s" % (int(number), encoded)),
            self.token,
            method="DELETE",
        )

    def get_parent(self, number):
        data, status = _json_request(
            self._url("/issues/%d/parent" % int(number)),
            self.token,
            allow_404=True,
        )
        if status == 404:
            return None
        return data

    def add_sub_issue(self, parent_number, child_id):
        url = self._url("/issues/%d/sub_issues" % int(parent_number))
        payload = {
            "sub_issue_id": int(child_id),
            "replace_parent": False,
        }
        for attempt in range(1, 4):
            try:
                data, _ = _json_request(
                    url,
                    self.token,
                    method="POST",
                    payload=payload,
                )
                return data
            except ApiError as exc:
                low = exc.detail.lower()
                limited = (
                    exc.status in (403, 422)
                    and (
                        "secondary rate" in low
                        or "spam" in low
                        or "rate limit" in low
                    )
                )
                if limited and attempt < 3:
                    time.sleep(2 ** attempt)
                    continue
                raise


def label_names(issue):
    result = []
    for label in issue.get("labels") or []:
        if isinstance(label, dict):
            name = label.get("name")
        else:
            name = label
        if name:
            result.append(name)
    return result


def parse_title_identifier(title):
    match = TITLE_IDENTIFIER_RE.match(title or "")
    if not match:
        return None
    return {
        "identifier": match.group("identifier"),
        "family": match.group("family"),
    }


def parse_sections(body):
    sections = {}
    for match in SECTION_RE.finditer(body or ""):
        name = match.group(1).strip()
        value = match.group(2).strip()
        sections[name] = value
    return sections


def first_meaningful_line(value):
    for line in (value or "").splitlines():
        line = line.strip()
        if not line:
            continue
        if line == "_No response_":
            return ""
        return line
    return ""


def parse_work_form_identifier(body):
    sections = parse_sections(body)
    signature = (
        "Work item",
        "Goal",
        "Authority and design records",
        "Dependencies",
        "Non-goals",
        "Acceptance criteria",
        "Validation",
        "Governance boundary",
    )
    if not all(name in sections for name in signature):
        return None
    value = first_meaningful_line(sections.get("Work item"))
    match = STRICT_WORK_ITEM_RE.match(value)
    if not match:
        raise IntakeError(
            "tracked-work form contains an invalid Work item; expected "
            "`<IDENTIFIER> — <short name>`"
        )
    return {
        "identifier": match.group("identifier"),
        "family": match.group("family"),
    }


def declared_parent_numbers(body):
    result = set(int(value) for value in PARENT_LINE_RE.findall(body or ""))

    sections = parse_sections(body)
    if "Parent issue" in sections:
        value = first_meaningful_line(sections["Parent issue"])
        normalized = value.strip().lower()
        if normalized and normalized not in ("none", "n/a", "no", "-"):
            match = re.fullmatch(r"#?(\d+)", value.strip())
            if not match:
                raise IntakeError(
                    "Parent issue form value must be `#N`/`N` or `none`"
                )
            result.add(int(match.group(1)))

    return result


def is_child_identifier(identifier):
    return "-" in identifier


def is_authorized_formal(issue):
    association = (issue.get("author_association") or "").upper()
    if association in TRUSTED_AUTHOR_ASSOCIATIONS:
        return True
    return any(
        name.startswith(FAMILY_LABEL_PREFIX)
        for name in label_names(issue)
    )


def reconcile_family(api, issue, expected_family):
    number = int(issue["number"])
    expected = FAMILY_LABEL_PREFIX + expected_family
    families = [
        name for name in label_names(issue)
        if name.startswith(FAMILY_LABEL_PREFIX)
    ]

    removed = []
    for name in families:
        if name != expected:
            api.remove_label(number, name)
            removed.append(name)
            print("ISSUE_FAMILY_LABEL_REMOVED: #%d %s" % (number, name))

    added = False
    if expected not in families:
        api.add_labels(number, [expected])
        added = True
        print("ISSUE_FAMILY_LABEL_ADDED: #%d %s" % (number, expected))

    return {"added": added, "removed": removed}


def reconcile_parent(api, issue, identifier):
    number = int(issue["number"])
    declarations = declared_parent_numbers(issue.get("body") or "")

    if len(declarations) > 1:
        raise IntakeError(
            "Issue #%d declares multiple parent Issues: %s"
            % (number, ", ".join("#%d" % n for n in sorted(declarations)))
        )

    declared = next(iter(declarations)) if declarations else None
    native = api.get_parent(number)
    native_number = int(native["number"]) if native else None

    if native_number is not None:
        if declared is not None and declared != native_number:
            raise IntakeError(
                "Issue #%d parent conflict: native #%d, textual/form #%d"
                % (number, native_number, declared)
            )
        return {
            "native_parent": native_number,
            "added": False,
            "declared_parent": declared,
        }

    if declared is None:
        if is_child_identifier(identifier):
            raise IntakeError(
                "Issue #%d (%s) is a formal child but has no native parent "
                "and no explicit parent declaration to reconcile"
                % (number, identifier)
            )
        return {
            "native_parent": None,
            "added": False,
            "declared_parent": None,
        }

    if declared == number:
        raise IntakeError("Issue #%d cannot be its own parent" % number)

    parent = api.fetch_issue(declared)
    if int(parent["number"]) != declared:
        raise IntakeError("parent resolution mismatch for Issue #%d" % number)

    api.add_sub_issue(declared, issue["id"])

    verified = api.get_parent(number)
    if not verified or int(verified["number"]) != declared:
        raise IntakeError(
            "native parent verification failed for Issue #%d; expected #%d"
            % (number, declared)
        )

    print(
        "NATIVE_PARENT_ADDED: child=#%d parent=#%d"
        % (number, declared)
    )
    return {
        "native_parent": declared,
        "added": True,
        "declared_parent": declared,
    }


def reconcile_issue(api, issue):
    number = int(issue["number"])
    title_info = parse_title_identifier(issue.get("title") or "")
    form_info = parse_work_form_identifier(issue.get("body") or "")

    if form_info is not None:
        if title_info is None:
            raise IntakeError(
                "Issue #%d uses the tracked-work form but its title does not "
                "begin with the formal identifier" % number
            )
        if title_info["identifier"] != form_info["identifier"]:
            raise IntakeError(
                "Issue #%d title/form identifier mismatch: %s != %s"
                % (
                    number,
                    title_info["identifier"],
                    form_info["identifier"],
                )
            )

    if title_info is None:
        return {
            "number": number,
            "kind": "community",
            "formal": False,
            "changed_family": False,
            "added_parent": False,
        }

    if not is_authorized_formal(issue):
        print(
            "FORMAL_CANDIDATE_LEFT_IN_TRIAGE: #%d author_association=%s"
            % (number, issue.get("author_association") or "NONE")
        )
        return {
            "number": number,
            "kind": "untrusted-formal-candidate",
            "formal": False,
            "changed_family": False,
            "added_parent": False,
        }

    family_result = reconcile_family(api, issue, title_info["family"])
    parent_result = reconcile_parent(api, issue, title_info["identifier"])

    return {
        "number": number,
        "kind": "formal",
        "formal": True,
        "identifier": title_info["identifier"],
        "family": title_info["family"],
        "changed_family": bool(
            family_result["added"] or family_result["removed"]
        ),
        "added_parent": parent_result["added"],
        "parent": parent_result["native_parent"],
    }


def run_one(api, number):
    issue = api.fetch_issue(number)
    if issue.get("state") != "open":
        print("ISSUE_INTAKE_SKIPPED_CLOSED: #%d" % int(number))
        return 0

    result = reconcile_issue(api, issue)
    print(
        "ISSUE_INTAKE: PASS issue=#%d kind=%s"
        % (int(number), result["kind"])
    )
    return 0


def run_all(api):
    issues = api.list_open_issues()
    counts = {
        "open": len(issues),
        "formal": 0,
        "community": 0,
        "untrusted": 0,
        "family_changed": 0,
        "parents_added": 0,
    }
    failures = []

    for issue in issues:
        number = int(issue["number"])
        try:
            result = reconcile_issue(api, issue)
            if result["kind"] == "formal":
                counts["formal"] += 1
            elif result["kind"] == "community":
                counts["community"] += 1
            else:
                counts["untrusted"] += 1
            if result.get("changed_family"):
                counts["family_changed"] += 1
            if result.get("added_parent"):
                counts["parents_added"] += 1
        except IntakeError as exc:
            failures.append((number, str(exc)))
            print(
                "ISSUE_INTAKE_RECONCILE_ERROR: #%d %s"
                % (number, exc),
                file=sys.stderr,
            )

    print("OPEN_ISSUES_SCANNED=%d" % counts["open"])
    print("FORMAL_ISSUES_RECONCILED=%d" % counts["formal"])
    print("COMMUNITY_ISSUES_OBSERVED=%d" % counts["community"])
    print("UNTRUSTED_FORMAL_CANDIDATES=%d" % counts["untrusted"])
    print("FAMILY_RECONCILIATIONS=%d" % counts["family_changed"])
    print("NATIVE_PARENTS_ADDED=%d" % counts["parents_added"])
    print("UNRESOLVED_INTAKE_ERRORS=%d" % len(failures))

    if failures:
        print("ISSUE_INTAKE_RECONCILIATION: FAIL_CLOSED", file=sys.stderr)
        return 2

    print("ISSUE_INTAKE_RECONCILIATION: PASS")
    return 0


class MockApi(object):
    def __init__(self):
        self.issues = {}
        self.parents = {}
        self.added_labels = []
        self.removed_labels = []
        self.added_parents = []

    def fetch_issue(self, number):
        return self.issues[int(number)]

    def add_labels(self, number, names):
        self.added_labels.append((int(number), tuple(names)))
        issue = self.issues[int(number)]
        existing = label_names(issue)
        for name in names:
            if name not in existing:
                issue.setdefault("labels", []).append({"name": name})

    def remove_label(self, number, name):
        self.removed_labels.append((int(number), name))
        issue = self.issues[int(number)]
        issue["labels"] = [
            label for label in issue.get("labels") or []
            if (label.get("name") if isinstance(label, dict) else label) != name
        ]

    def get_parent(self, number):
        parent = self.parents.get(int(number))
        if parent is None:
            return None
        return {"number": parent}

    def add_sub_issue(self, parent_number, child_id):
        child_number = None
        for number, issue in self.issues.items():
            if int(issue["id"]) == int(child_id):
                child_number = number
                break
        if child_number is None:
            raise AssertionError("mock child id not found")
        self.parents[child_number] = int(parent_number)
        self.added_parents.append((int(parent_number), child_number))


def _mock_issue(
    number,
    title,
    body="",
    labels=None,
    association="OWNER",
    issue_id=None,
):
    return {
        "number": int(number),
        "id": int(issue_id if issue_id is not None else number * 1000),
        "title": title,
        "body": body,
        "labels": [{"name": name} for name in (labels or [])],
        "author_association": association,
        "state": "open",
    }


def self_test():
    assert parse_title_identifier("I031 — Object work") == {
        "identifier": "I031",
        "family": "I",
    }
    assert parse_title_identifier("TOOL001-F2E4 — External execution") == {
        "identifier": "TOOL001-F2E4",
        "family": "TOOL",
    }
    assert parse_title_identifier("PERF001-F blocker — detail") == {
        "identifier": "PERF001-F",
        "family": "PERF",
    }
    assert parse_title_identifier("[Bug] parser breaks") is None

    assert declared_parent_numbers("Parent: #288\n") == {288}
    assert declared_parent_numbers("**Parent work item:** #47 (`TOOL001`)\n") == {47}
    assert declared_parent_numbers(
        "### Parent issue\n\n#52\n\n### Goal\n\nx\n"
    ) == {52}
    assert declared_parent_numbers(
        "### Parent issue\n\nnone\n\n### Goal\n\nx\n"
    ) == set()

    mock = MockApi()
    mock.issues[319] = _mock_issue(
        319,
        "GITHUB007 — Issue intake",
        labels=["family:D", "status:in-progress", "priority:p2"],
    )
    top = reconcile_issue(mock, mock.issues[319])
    assert top["formal"]
    assert (319, "family:D") in mock.removed_labels
    assert (319, ("family:GITHUB",)) in mock.added_labels

    mock = MockApi()
    mock.issues[288] = _mock_issue(288, "LM009 — Editor maturity")
    mock.issues[318] = _mock_issue(
        318,
        "LM009-E — VS Code debugging integration",
        body="## Parent\n\nParent: #288 (`LM009`)\n",
        labels=["family:LM", "status:blocked"],
    )
    child = reconcile_issue(mock, mock.issues[318])
    assert child["added_parent"]
    assert mock.parents[318] == 288

    mock.parents[318] = 287
    try:
        reconcile_issue(mock, mock.issues[318])
    except IntakeError as exc:
        assert "parent conflict" in str(exc)
    else:
        raise AssertionError("conflicting native parent did not fail")

    untrusted = _mock_issue(
        999,
        "I999 — forged formal item",
        labels=["status:inbox"],
        association="NONE",
    )
    mock = MockApi()
    mock.issues[999] = untrusted
    result = reconcile_issue(mock, untrusted)
    assert result["kind"] == "untrusted-formal-candidate"
    assert not mock.added_labels
    assert not mock.added_parents

    community = _mock_issue(
        998,
        "[Request] Better example",
        labels=["status:inbox"],
        association="NONE",
    )
    mock.issues[998] = community
    result = reconcile_issue(mock, community)
    assert result["kind"] == "community"

    orphan = _mock_issue(
        400,
        "DOC001-N — Final docs",
        labels=["family:DOC", "status:inbox"],
    )
    mock = MockApi()
    mock.issues[400] = orphan
    try:
        reconcile_issue(mock, orphan)
    except IntakeError as exc:
        assert "formal child" in str(exc)
    else:
        raise AssertionError("formal orphan did not fail closed")

    form_body = """\
### Work item

CLI008 — Diagnostics

### Parent issue

none

### Goal

x

### Authority and design records

x

### Dependencies

none

### Non-goals

x

### Acceptance criteria

- [ ] x

### Validation

x

### Governance boundary

- [x] confirmed
"""
    assert parse_work_form_identifier(form_body)["identifier"] == "CLI008"

    print("ISSUE_INTAKE_SELF_TEST: PASS")
    print("FORMAL_FAMILY_DERIVATION: PASS")
    print("FORMAL_PARENT_RECONCILIATION: PASS")
    print("PARENT_CONFLICT_FAIL_CLOSED: PASS")
    print("UNTRUSTED_FORMAL_CANDIDATE_NO_PROMOTION: PASS")
    print("COMMUNITY_NO_FAMILY_PROMOTION: PASS")
    print("FORMAL_ORPHAN_FAIL_CLOSED: PASS")


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", default="guillermomolina/protos")
    parser.add_argument("--issue-number", type=int)
    parser.add_argument("--reconcile-all", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        self_test()
        return 0

    if args.repository != "guillermomolina/protos":
        print(
            "ISSUE_INTAKE: FAIL unexpected repository %r" % args.repository,
            file=sys.stderr,
        )
        return 2

    if bool(args.issue_number) == bool(args.reconcile_all):
        print(
            "ISSUE_INTAKE: FAIL choose exactly one of --issue-number or "
            "--reconcile-all",
            file=sys.stderr,
        )
        return 2

    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        print("ISSUE_INTAKE: FAIL GITHUB_TOKEN is required", file=sys.stderr)
        return 2

    api = GitHubApi(args.repository, token)

    try:
        if args.reconcile_all:
            return run_all(api)
        return run_one(api, args.issue_number)
    except IntakeError as exc:
        print("ISSUE_INTAKE: FAIL_CLOSED", file=sys.stderr)
        print("ISSUE_INTAKE_ERROR: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
