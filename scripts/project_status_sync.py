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

"""Synchronize Issue-owned Protos status/priority into Protos Development."""

from __future__ import print_function

import argparse
import json
import os
import sys
from urllib import error, parse, request

STATUS_TO_PROJECT = {
    "status:inbox": "Inbox",
    "status:ready": "Ready",
    "status:in-progress": "In progress",
    "status:needs-decision": "Needs decision",
    "status:blocked": "Blocked",
    "status:paused": "Paused",
    "status:review": "Review",
}
STATUS_LABELS = frozenset(STATUS_TO_PROJECT)

PRIORITY_TO_PROJECT = {
    "priority:p0": "P0",
    "priority:p1": "P1",
    "priority:p2": "P2",
    "priority:p3": "P3",
}
PRIORITY_LABELS = frozenset(PRIORITY_TO_PROJECT)
PRIORITY_LABEL_DEFINITIONS = {
    "priority:p0": ("b60205", "Immediate/critical scheduling priority; exceptional."),
    "priority:p1": ("d93f0b", "Next/high-priority scheduled work."),
    "priority:p2": ("fbca04", "Normal planned scheduling priority."),
    "priority:p3": ("0e8a16", "Opportunistic/later scheduling priority."),
}


class SyncError(Exception):
    pass


def _json_request(url, token, method="GET", payload=None):
    data = None
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + token,
        "User-Agent": "protos-project-issue-sync",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = request.Request(url, data=data, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=30) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else None, response.headers
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")
        raise SyncError(
            "%s %s failed: HTTP %s: %s"
            % (method, url, exc.code, detail[:1000])
        )
    except error.URLError as exc:
        raise SyncError("%s %s failed: %s" % (method, url, exc))


def _rest(repository, path, token, method="GET", payload=None):
    url = "https://api.github.com/repos/%s%s" % (repository, path)
    return _json_request(url, token, method=method, payload=payload)[0]


def _graphql(token, query, variables):
    data, _ = _json_request(
        "https://api.github.com/graphql",
        token,
        method="POST",
        payload={"query": query, "variables": variables},
    )
    errors = (data or {}).get("errors")
    if errors:
        raise SyncError("GraphQL failed: " + json.dumps(errors, sort_keys=True))
    if not data or "data" not in data:
        raise SyncError("GraphQL response did not contain data")
    return data["data"]


def _label_names(issue):
    names = []
    for label in issue.get("labels") or []:
        name = label.get("name") if isinstance(label, dict) else label
        if name:
            names.append(name)
    return names


def choose_open_status(issue, event_action=None, event_label=None):
    labels = [name for name in _label_names(issue) if name in STATUS_LABELS]
    if event_action == "labeled" and event_label in STATUS_LABELS:
        return event_label, [name for name in labels if name != event_label], False
    if len(labels) == 1:
        return labels[0], [], False
    if len(labels) == 0:
        return "status:inbox", [], True
    raise SyncError(
        "Issue #%s has multiple status labels without a decisive newly-added "
        "status label: %s"
        % (issue.get("number"), ", ".join(sorted(labels)))
    )


def choose_open_priority(issue, event_action=None, event_label=None):
    labels = [name for name in _label_names(issue) if name in PRIORITY_LABELS]
    if event_action == "labeled" and event_label in PRIORITY_LABELS:
        return event_label, [name for name in labels if name != event_label]
    if len(labels) == 1:
        return labels[0], []
    if len(labels) == 0:
        return None, []
    raise SyncError(
        "Issue #%s has multiple priority labels without a decisive newly-added "
        "priority label: %s"
        % (issue.get("number"), ", ".join(sorted(labels)))
    )


def _remove_issue_labels(repository, number, issue_token, removals, kind):
    for label in removals:
        encoded = parse.quote(label, safe="")
        _rest(
            repository,
            "/issues/%d/labels/%s" % (number, encoded),
            issue_token,
            method="DELETE",
        )
        print("ISSUE_%s_LABEL_REMOVED: #%d %s" % (kind, number, label))


def _set_issue_status_label(repository, issue, issue_token, canonical, removals, add):
    number = int(issue["number"])
    _remove_issue_labels(
        repository, number, issue_token, removals, "STATUS"
    )
    if add:
        _rest(
            repository,
            "/issues/%d/labels" % number,
            issue_token,
            method="POST",
            payload={"labels": [canonical]},
        )
        print("ISSUE_STATUS_LABEL_ADDED: #%d %s" % (number, canonical))


def _set_issue_priority_label(repository, issue, issue_token, removals):
    number = int(issue["number"])
    _remove_issue_labels(
        repository, number, issue_token, removals, "PRIORITY"
    )


def _repository_label_names(repository, issue_token):
    names = set()
    page = 1
    while True:
        batch = _rest(
            repository,
            "/labels?per_page=100&page=%d" % page,
            issue_token,
        )
        if not batch:
            break
        for label in batch:
            if label.get("name"):
                names.add(label["name"])
        if len(batch) < 100:
            break
        page += 1
    return names


def ensure_priority_labels(repository, issue_token):
    existing = _repository_label_names(repository, issue_token)
    for name in sorted(PRIORITY_LABEL_DEFINITIONS):
        if name in existing:
            continue
        color, description = PRIORITY_LABEL_DEFINITIONS[name]
        _rest(
            repository,
            "/labels",
            issue_token,
            method="POST",
            payload={
                "name": name,
                "color": color,
                "description": description,
            },
        )
        print("PRIORITY_LABEL_CREATED: %s" % name)


PROJECT_QUERY = r"""
query($login: String!, $number: Int!) {
  user(login: $login) {
    projectV2(number: $number) {
      id
      title
      fields(first: 100) {
        nodes {
          ... on ProjectV2SingleSelectField {
            id
            name
            options {
              id
              name
            }
          }
        }
      }
    }
  }
}
"""

ITEMS_QUERY = r"""
query($login: String!, $number: Int!, $after: String) {
  user(login: $login) {
    projectV2(number: $number) {
      items(first: 100, after: $after) {
        nodes {
          id
          content {
            ... on Issue {
              id
              number
              state
              repository {
                nameWithOwner
              }
            }
          }
        }
        pageInfo {
          hasNextPage
          endCursor
        }
      }
    }
  }
}
"""

ADD_ITEM_MUTATION = r"""
mutation($project: ID!, $content: ID!) {
  addProjectV2ItemById(input: {projectId: $project, contentId: $content}) {
    item { id }
  }
}
"""

UPDATE_SINGLE_SELECT_MUTATION = r"""
mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
  updateProjectV2ItemFieldValue(
    input: {
      projectId: $project,
      itemId: $item,
      fieldId: $field,
      value: {singleSelectOptionId: $option}
    }
  ) {
    projectV2Item { id }
  }
}
"""

CLEAR_FIELD_MUTATION = r"""
mutation($project: ID!, $item: ID!, $field: ID!) {
  clearProjectV2ItemFieldValue(
    input: {
      projectId: $project,
      itemId: $item,
      fieldId: $field
    }
  ) {
    projectV2Item { id }
  }
}
"""


def _single_select_field(project, name):
    for node in (project.get("fields") or {}).get("nodes") or []:
        if node and node.get("name") == name:
            options = {
                option["name"]: option["id"]
                for option in node.get("options") or []
                if option.get("name") and option.get("id")
            }
            return {"id": node["id"], "options": options}
    raise SyncError("Project does not contain a single-select %s field" % name)


def load_project(project_token, owner, number):
    data = _graphql(
        project_token, PROJECT_QUERY, {"login": owner, "number": int(number)}
    )
    project = (data.get("user") or {}).get("projectV2")
    if not project:
        raise SyncError(
            "Cannot resolve user Project #%s for %s; check PROTOS_PROJECT_TOKEN "
            "and its project scope." % (number, owner)
        )

    status_field = _single_select_field(project, "Status")
    priority_field = _single_select_field(project, "Priority")

    missing_status = sorted(
        set(STATUS_TO_PROJECT.values()) - set(status_field["options"])
    )
    if missing_status:
        raise SyncError(
            "Project Status options missing: " + ", ".join(missing_status)
        )

    missing_priority = sorted(
        set(PRIORITY_TO_PROJECT.values()) - set(priority_field["options"])
    )
    if missing_priority:
        raise SyncError(
            "Project Priority options missing: " + ", ".join(missing_priority)
        )

    return {
        "id": project["id"],
        "title": project.get("title"),
        "status": status_field,
        "priority": priority_field,
    }


def load_project_issue_items(project_token, owner, number):
    result = {}
    project_issues = []
    after = None
    while True:
        data = _graphql(
            project_token,
            ITEMS_QUERY,
            {"login": owner, "number": int(number), "after": after},
        )
        project = (data.get("user") or {}).get("projectV2")
        if not project:
            raise SyncError("Project disappeared while enumerating items")
        connection = project["items"]
        for node in connection.get("nodes") or []:
            content = node and node.get("content")
            repository = content and content.get("repository")
            if not content or not repository:
                continue
            repo_name = repository.get("nameWithOwner")
            key = (repo_name, int(content["number"]))
            result[key] = node["id"]
            project_issues.append({
                "item_id": node["id"],
                "repository": repo_name,
                "number": int(content["number"]),
                "state": content.get("state"),
            })
        page = connection["pageInfo"]
        if not page["hasNextPage"]:
            break
        after = page["endCursor"]
        if not after:
            raise SyncError("Project item pagination lost its end cursor")
    return result, project_issues


def ensure_project_item(project_token, project, item_map, repository, issue):
    key = (repository, int(issue["number"]))
    existing = item_map.get(key)
    if existing:
        return existing, False
    data = _graphql(
        project_token,
        ADD_ITEM_MUTATION,
        {"project": project["id"], "content": issue["node_id"]},
    )
    item = (data.get("addProjectV2ItemById") or {}).get("item")
    if not item or not item.get("id"):
        raise SyncError("Project add-item mutation returned no item id")
    item_map[key] = item["id"]
    return item["id"], True


def _project_option(project, field_name, option_name):
    option_id = project[field_name]["options"].get(option_name)
    if option_id:
        return option_id
    raise SyncError(
        "Project %s option %r is missing. Current options: %s"
        % (
            field_name.title(),
            option_name,
            ", ".join(sorted(project[field_name]["options"])),
        )
    )


def set_project_single_select(
    project_token, project, item_id, field_name, option_name
):
    _graphql(
        project_token,
        UPDATE_SINGLE_SELECT_MUTATION,
        {
            "project": project["id"],
            "item": item_id,
            "field": project[field_name]["id"],
            "option": _project_option(project, field_name, option_name),
        },
    )


def clear_project_field(project_token, project, item_id, field_name):
    _graphql(
        project_token,
        CLEAR_FIELD_MUTATION,
        {
            "project": project["id"],
            "item": item_id,
            "field": project[field_name]["id"],
        },
    )


def set_project_status(project_token, project, item_id, status_name):
    set_project_single_select(
        project_token, project, item_id, "status", status_name
    )


def set_project_priority(project_token, project, item_id, priority_name):
    set_project_single_select(
        project_token, project, item_id, "priority", priority_name
    )


def fetch_issue(repository, number, issue_token):
    return _rest(repository, "/issues/%d" % int(number), issue_token)


def sync_issue(
    repository,
    issue,
    project_token,
    issue_token,
    project,
    item_map,
    event_action=None,
    event_label=None,
):
    number = int(issue["number"])
    if issue.get("pull_request"):
        print("SKIP_PULL_REQUEST: #%d" % number)
        return

    key = (repository, number)
    if issue.get("state") == "closed":
        item_id = item_map.get(key)
        if not item_id:
            print("SKIP_CLOSED_UNTRACKED: #%d" % number)
            return
        set_project_status(project_token, project, item_id, "Done")
        print("PROJECT_STATUS: #%d -> Done" % number)
        return

    canonical_status, status_removals, add_status = choose_open_status(
        issue, event_action=event_action, event_label=event_label
    )
    canonical_priority, priority_removals = choose_open_priority(
        issue, event_action=event_action, event_label=event_label
    )

    _set_issue_status_label(
        repository,
        issue,
        issue_token,
        canonical_status,
        status_removals,
        add_status,
    )
    _set_issue_priority_label(
        repository, issue, issue_token, priority_removals
    )

    item_id, added = ensure_project_item(
        project_token, project, item_map, repository, issue
    )
    if added:
        print("PROJECT_ITEM_ADDED: #%d" % number)

    status_name = STATUS_TO_PROJECT[canonical_status]
    set_project_status(project_token, project, item_id, status_name)
    print(
        "PROJECT_STATUS: #%d -> %s (%s)"
        % (number, status_name, canonical_status)
    )

    if canonical_priority is None:
        clear_project_field(project_token, project, item_id, "priority")
        print("PROJECT_PRIORITY: #%d -> <unset>" % number)
    else:
        priority_name = PRIORITY_TO_PROJECT[canonical_priority]
        set_project_priority(
            project_token, project, item_id, priority_name
        )
        print(
            "PROJECT_PRIORITY: #%d -> %s (%s)"
            % (number, priority_name, canonical_priority)
        )


def list_open_issues(repository, issue_token):
    page = 1
    while True:
        batch = _rest(
            repository,
            "/issues?state=open&per_page=100&page=%d" % page,
            issue_token,
        )
        if not batch:
            return
        for issue in batch:
            if not issue.get("pull_request"):
                yield issue
        if len(batch) < 100:
            return
        page += 1


def run_reconcile(
    args, project_token, issue_token, project, item_map, project_issues
):
    failures = []
    closed_count = 0
    for tracked in project_issues:
        if (
            tracked["repository"] == args.repository
            and tracked.get("state") == "CLOSED"
        ):
            try:
                set_project_status(
                    project_token, project, tracked["item_id"], "Done"
                )
                closed_count += 1
                print(
                    "PROJECT_STATUS: #%d -> Done (legacy reconciliation)"
                    % tracked["number"]
                )
            except SyncError as exc:
                failures.append((tracked["number"], str(exc)))
                print(
                    "RECONCILE_CLOSED_FAILED: #%s %s"
                    % (tracked["number"], exc),
                    file=sys.stderr,
                )

    open_count = 0
    for issue in list_open_issues(args.repository, issue_token):
        open_count += 1
        try:
            sync_issue(
                args.repository,
                issue,
                project_token,
                issue_token,
                project,
                item_map,
            )
        except SyncError as exc:
            failures.append((issue.get("number"), str(exc)))
            print(
                "RECONCILE_ISSUE_FAILED: #%s %s"
                % (issue.get("number"), exc),
                file=sys.stderr,
            )

    print("RECONCILE_CLOSED_TRACKED_COUNT: %d" % closed_count)
    print("RECONCILE_OPEN_COUNT: %d" % open_count)
    if failures:
        raise SyncError(
            "Project/Issue reconciliation completed with %d failure(s)"
            % len(failures)
        )


def _fake_issue(number, state="open", labels=None):
    return {
        "number": number,
        "state": state,
        "labels": [{"name": name} for name in (labels or [])],
        "node_id": "I_fake_%d" % number,
    }


def self_test():
    assert choose_open_status(
        _fake_issue(1, labels=["status:ready"])
    ) == ("status:ready", [], False)
    assert choose_open_status(
        _fake_issue(2, labels=[])
    ) == ("status:inbox", [], True)
    assert choose_open_status(
        _fake_issue(
            3, labels=["status:ready", "status:in-progress"]
        ),
        event_action="labeled",
        event_label="status:in-progress",
    ) == ("status:in-progress", ["status:ready"], False)

    try:
        choose_open_status(
            _fake_issue(
                4, labels=["status:ready", "status:blocked"]
            )
        )
    except SyncError:
        pass
    else:
        raise AssertionError("multiple status labels must fail closed")

    assert choose_open_priority(
        _fake_issue(5, labels=["priority:p1"])
    ) == ("priority:p1", [])
    assert choose_open_priority(
        _fake_issue(6, labels=[])
    ) == (None, [])
    assert choose_open_priority(
        _fake_issue(
            7, labels=["priority:p2", "priority:p0"]
        ),
        event_action="labeled",
        event_label="priority:p0",
    ) == ("priority:p0", ["priority:p2"])

    try:
        choose_open_priority(
            _fake_issue(
                8, labels=["priority:p1", "priority:p2"]
            )
        )
    except SyncError:
        pass
    else:
        raise AssertionError("multiple priority labels must fail closed")

    assert STATUS_TO_PROJECT["status:paused"] == "Paused"
    assert len(STATUS_TO_PROJECT) == 7
    assert PRIORITY_TO_PROJECT["priority:p0"] == "P0"
    assert PRIORITY_TO_PROJECT["priority:p3"] == "P3"
    assert len(PRIORITY_TO_PROJECT) == 4
    print("PROJECT_STATUS_PRIORITY_SYNC_SELF_TEST: PASS")


def parse_args(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repository", default=os.environ.get("GITHUB_REPOSITORY")
    )
    parser.add_argument("--project-owner", default="guillermomolina")
    parser.add_argument("--project-number", type=int, default=1)
    parser.add_argument("--issue-number", type=int)
    parser.add_argument("--event-action")
    parser.add_argument("--event-label")
    parser.add_argument("--reconcile-all", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    if args.self_test:
        self_test()
        return 0
    if not args.repository:
        raise SyncError("--repository or GITHUB_REPOSITORY is required")
    if bool(args.issue_number) == bool(args.reconcile_all):
        raise SyncError(
            "select exactly one of --issue-number or --reconcile-all"
        )

    project_token = os.environ.get("PROTOS_PROJECT_TOKEN", "")
    issue_token = os.environ.get("GITHUB_TOKEN", "")
    if not project_token:
        raise SyncError(
            "PROTOS_PROJECT_TOKEN is missing; configure the repository Actions "
            "secret with a token that can write the user-owned Project."
        )
    if not issue_token:
        raise SyncError("GITHUB_TOKEN is missing")

    if args.reconcile_all:
        ensure_priority_labels(args.repository, issue_token)

    project = load_project(
        project_token, args.project_owner, args.project_number
    )
    print(
        "PROJECT_RESOLVED: #%d %s"
        % (
            args.project_number,
            project.get("title") or "<untitled>",
        )
    )

    item_map, project_issues = load_project_issue_items(
        project_token, args.project_owner, args.project_number
    )

    if args.reconcile_all:
        run_reconcile(
            args,
            project_token,
            issue_token,
            project,
            item_map,
            project_issues,
        )
        return 0

    issue = fetch_issue(
        args.repository, args.issue_number, issue_token
    )
    sync_issue(
        args.repository,
        issue,
        project_token,
        issue_token,
        project,
        item_map,
        event_action=args.event_action,
        event_label=args.event_label,
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SyncError as exc:
        print("PROJECT_STATUS_PRIORITY_SYNC: FAIL", file=sys.stderr)
        print(
            "PROJECT_STATUS_PRIORITY_SYNC_ERROR: " + str(exc),
            file=sys.stderr,
        )
        sys.exit(2)
