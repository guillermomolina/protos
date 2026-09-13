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
STATUS_LABEL_PREFIX = "status:"

PRIORITY_TO_PROJECT = {
    "priority:p0": "P0",
    "priority:p1": "P1",
    "priority:p2": "P2",
    "priority:p3": "P3",
}
PROJECT_TO_PRIORITY = dict(
    (project_name, label_name)
    for label_name, project_name in PRIORITY_TO_PROJECT.items()
)
PRIORITY_LABELS = frozenset(PRIORITY_TO_PROJECT)
PRIORITY_LABEL_DEFINITIONS = {
    "priority:p0": ("b60205", "Immediate/critical scheduling priority; exceptional."),
    "priority:p1": ("d93f0b", "Next/high-priority scheduled work."),
    "priority:p2": ("fbca04", "Normal planned scheduling priority."),
    "priority:p3": ("0e8a16", "Opportunistic/later scheduling priority."),
}
DEFAULT_IN_PROGRESS_ASSIGNEE = "guillermomolina"
ASSIGNEE_REQUIRED_STATUSES = frozenset((
    "status:in-progress",
    "status:needs-decision",
))
PRIORITY_REQUIRED_STATUSES = frozenset((
    "status:in-progress",
    "status:needs-decision",
))
MAX_PRIORITY_ANCESTRY_DEPTH = 32


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


def _is_formal_issue(issue):
    return any(
        name.startswith("family:") for name in _label_names(issue)
    )


def choose_open_status(issue, event_action=None, event_label=None):
    status_labels = [
        name for name in _label_names(issue)
        if name.startswith(STATUS_LABEL_PREFIX)
    ]
    unknown = sorted(set(
        name for name in status_labels if name not in STATUS_LABELS
    ))
    if unknown:
        raise SyncError(
            "Issue #%s has unsupported status label(s): %s; allowed: %s"
            % (
                issue.get("number"),
                ", ".join(unknown),
                ", ".join(sorted(STATUS_LABELS)),
            )
        )
    labels = [name for name in status_labels if name in STATUS_LABELS]
    if event_action == "labeled" and event_label in STATUS_LABELS:
        return event_label, [name for name in labels if name != event_label], False
    if len(labels) == 1:
        return labels[0], [], False
    if len(labels) == 0:
        if _is_formal_issue(issue):
            raise SyncError(
                "Formal Issue #%s has no canonical status label; trusted "
                "formal publication must set lifecycle state explicitly "
                "instead of relying on the community Inbox default"
                % issue.get("number")
            )
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


def _assignee_logins(issue):
    result = []
    for assignee in issue.get("assignees") or []:
        login = assignee.get("login") if isinstance(assignee, dict) else assignee
        if login:
            result.append(login)
    return result


def needs_default_assignee(issue, canonical_status):
    return (
        canonical_status in ASSIGNEE_REQUIRED_STATUSES
        and not _assignee_logins(issue)
    )


def validate_required_effective_priority(issue, canonical_status, effective_priority):
    if (
        canonical_status in PRIORITY_REQUIRED_STATUSES
        and effective_priority is None
    ):
        raise SyncError(
            "Formal actionable Issue #%s is %s but has no explicit or "
            "inherited scheduling Priority; resolve Priority before treating "
            "publication as complete"
            % (issue.get("number"), canonical_status)
        )


def _parent_issue_number(issue):
    url = issue.get("parent_issue_url")
    if not url:
        return None
    raw = url.rstrip("/").rsplit("/", 1)[-1]
    try:
        return int(raw)
    except (TypeError, ValueError):
        raise SyncError(
            "Issue #%s has invalid parent_issue_url: %r"
            % (issue.get("number"), url)
        )


def resolve_effective_priority(
    repository, issue, issue_token, explicit_priority, fetcher=None
):
    if explicit_priority is not None:
        return explicit_priority, None

    if fetcher is None:
        fetcher = fetch_issue

    number = int(issue["number"])
    seen = set([number])
    parent_number = _parent_issue_number(issue)
    depth = 0

    while parent_number is not None:
        if parent_number in seen:
            raise SyncError(
                "Issue #%d priority ancestry contains a cycle at #%d"
                % (number, parent_number)
            )
        seen.add(parent_number)
        depth += 1
        if depth > MAX_PRIORITY_ANCESTRY_DEPTH:
            raise SyncError(
                "Issue #%d priority ancestry exceeds %d levels"
                % (number, MAX_PRIORITY_ANCESTRY_DEPTH)
            )

        parent = fetcher(repository, parent_number, issue_token)
        if not parent:
            raise SyncError(
                "Issue #%d cannot resolve parent #%d for priority inheritance"
                % (number, parent_number)
            )
        if parent.get("pull_request"):
            raise SyncError(
                "Issue #%d has pull request #%d in its parent chain"
                % (number, parent_number)
            )

        if parent.get("state") == "open":
            parent_priority, _ = choose_open_priority(parent)
            if parent_priority is not None:
                return parent_priority, parent_number

        parent_number = _parent_issue_number(parent)

    return None, None


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


def _add_issue_priority_label(repository, issue, issue_token, priority_label):
    number = int(issue["number"])
    _rest(
        repository,
        "/issues/%d/labels" % number,
        issue_token,
        method="POST",
        payload={"labels": [priority_label]},
    )
    print(
        "ISSUE_PRIORITY_LABEL_MIGRATED: #%d %s"
        % (number, priority_label)
    )


def priority_migration_candidate(
    effective_priority,
    current_project_priority,
    explicit_priority_removal=False,
):
    if effective_priority is not None:
        return None
    if explicit_priority_removal:
        return None
    if current_project_priority is None:
        return None
    priority_label = PROJECT_TO_PRIORITY.get(current_project_priority)
    if priority_label is None:
        raise SyncError(
            "Project Priority %r cannot be migrated; expected one of %s"
            % (
                current_project_priority,
                ", ".join(sorted(PROJECT_TO_PRIORITY)),
            )
        )
    return priority_label


def _ensure_required_assignee(
    repository, issue, issue_token, canonical_status
):
    if not needs_default_assignee(issue, canonical_status):
        return False

    number = int(issue["number"])
    updated = _rest(
        repository,
        "/issues/%d/assignees" % number,
        issue_token,
        method="POST",
        payload={"assignees": [DEFAULT_IN_PROGRESS_ASSIGNEE]},
    )
    assigned = _assignee_logins(updated or {})
    if DEFAULT_IN_PROGRESS_ASSIGNEE not in assigned:
        raise SyncError(
            "Issue #%d requires an assignee and GitHub did "
            "not accept default assignee %s"
            % (number, DEFAULT_IN_PROGRESS_ASSIGNEE)
        )
    issue["assignees"] = (updated or {}).get("assignees") or []
    print(
        "ISSUE_ASSIGNEE_ADDED: #%d %s"
        % (number, DEFAULT_IN_PROGRESS_ASSIGNEE)
    )
    return True


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
          fieldValues(first: 20) {
            nodes {
              ... on ProjectV2ItemFieldSingleSelectValue {
                name
                field {
                  ... on ProjectV2SingleSelectField {
                    name
                  }
                }
              }
            }
          }
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


def _project_item_single_select_value(node, field_name):
    for value in ((node.get("fieldValues") or {}).get("nodes") or []):
        if not value:
            continue
        field = value.get("field") or {}
        if field.get("name") == field_name:
            return value.get("name")
    return None


def load_project_issue_items(project_token, owner, number):
    result = {}
    project_issues = []
    project_priorities = {}
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
            project_priorities[key] = _project_item_single_select_value(
                node, "Priority"
            )
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
    return result, project_issues, project_priorities


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
    project_priorities,
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
    effective_priority, inherited_from = resolve_effective_priority(
        repository, issue, issue_token, canonical_priority
    )

    key = (repository, number)
    current_project_priority = project_priorities.get(key)
    explicit_priority_removal = (
        event_action == "unlabeled" and event_label in PRIORITY_LABELS
    )
    migration_priority = priority_migration_candidate(
        effective_priority,
        current_project_priority,
        explicit_priority_removal=explicit_priority_removal,
    )
    if migration_priority is not None:
        canonical_priority = migration_priority
        effective_priority = migration_priority
        inherited_from = None

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
    if migration_priority is not None:
        _add_issue_priority_label(
            repository, issue, issue_token, migration_priority
        )
    validate_required_effective_priority(
        issue, canonical_status, effective_priority
    )
    _ensure_required_assignee(
        repository, issue, issue_token, canonical_status
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

    if effective_priority is None:
        if explicit_priority_removal:
            clear_project_field(project_token, project, item_id, "priority")
            project_priorities[key] = None
            print(
                "PROJECT_PRIORITY: #%d -> <unset> "
                "(explicit priority label removal)" % number
            )
        elif current_project_priority is None:
            project_priorities[key] = None
            print(
                "PROJECT_PRIORITY: #%d -> <unchanged unset> "
                "(no explicit/inherited/migratable priority)" % number
            )
        else:
            raise SyncError(
                "Issue #%d has Project Priority %r but migration did not "
                "produce a durable priority label"
                % (number, current_project_priority)
            )
    else:
        priority_name = PRIORITY_TO_PROJECT[effective_priority]
        set_project_priority(
            project_token, project, item_id, priority_name
        )
        project_priorities[key] = priority_name
        if migration_priority is not None:
            print(
                "PROJECT_PRIORITY: #%d -> %s "
                "(%s migrated from existing Project Priority)"
                % (number, priority_name, effective_priority)
            )
        elif inherited_from is None:
            print(
                "PROJECT_PRIORITY: #%d -> %s (%s explicit)"
                % (number, priority_name, effective_priority)
            )
        else:
            print(
                "PROJECT_PRIORITY: #%d -> %s (%s inherited from #%d)"
                % (
                    number,
                    priority_name,
                    effective_priority,
                    inherited_from,
                )
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
    args,
    project_token,
    issue_token,
    project,
    item_map,
    project_issues,
    project_priorities,
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
                project_priorities,
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


def _fake_issue(
    number, state="open", labels=None, parent=None, assignees=None
):
    issue = {
        "number": number,
        "state": state,
        "labels": [{"name": name} for name in (labels or [])],
        "assignees": [{"login": login} for login in (assignees or [])],
        "node_id": "I_fake_%d" % number,
    }
    if parent is not None:
        issue["parent_issue_url"] = (
            "https://api.github.com/repos/guillermomolina/protos/issues/%d"
            % int(parent)
        )
    return issue


def self_test():
    assert choose_open_status(
        _fake_issue(1, labels=["status:ready"])
    ) == ("status:ready", [], False)
    assert choose_open_status(
        _fake_issue(2, labels=[])
    ) == ("status:inbox", [], True)
    try:
        choose_open_status(
            _fake_issue(200, labels=["family:LIB"])
        )
    except SyncError as exc:
        assert "trusted formal publication" in str(exc)
    else:
        raise AssertionError(
            "formal Issue without canonical status must fail closed"
        )
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

    try:
        choose_open_status(
            _fake_issue(9, labels=["status:needs-user-decision"])
        )
    except SyncError as exc:
        assert "unsupported status label" in str(exc)
    else:
        raise AssertionError("unknown status:* label must fail closed")

    assert STATUS_TO_PROJECT["status:paused"] == "Paused"
    assert len(STATUS_TO_PROJECT) == 7
    assert PRIORITY_TO_PROJECT["priority:p0"] == "P0"
    assert PRIORITY_TO_PROJECT["priority:p3"] == "P3"
    assert len(PRIORITY_TO_PROJECT) == 4

    assert needs_default_assignee(
        _fake_issue(10, labels=["status:in-progress"]),
        "status:in-progress",
    )
    assert not needs_default_assignee(
        _fake_issue(11, labels=["status:ready"]),
        "status:ready",
    )
    assert not needs_default_assignee(
        _fake_issue(12, labels=["status:in-progress"], assignees=["alice"]),
        "status:in-progress",
    )
    assert needs_default_assignee(
        _fake_issue(13, labels=["status:needs-decision"]),
        "status:needs-decision",
    )
    assert not needs_default_assignee(
        _fake_issue(14, labels=["status:needs-decision"], assignees=["alice"]),
        "status:needs-decision",
    )

    try:
        validate_required_effective_priority(
            _fake_issue(15, labels=["status:in-progress"]),
            "status:in-progress",
            None,
        )
    except SyncError:
        pass
    else:
        raise AssertionError("In progress without effective Priority must fail")

    try:
        validate_required_effective_priority(
            _fake_issue(16, labels=["status:needs-decision"]),
            "status:needs-decision",
            None,
        )
    except SyncError:
        pass
    else:
        raise AssertionError("Needs decision without effective Priority must fail")

    validate_required_effective_priority(
        _fake_issue(17, labels=["status:ready"]),
        "status:ready",
        None,
    )
    validate_required_effective_priority(
        _fake_issue(18, labels=["status:needs-decision"]),
        "status:needs-decision",
        "priority:p3",
    )

    parents = {
        20: _fake_issue(20, labels=["priority:p1"]),
        21: _fake_issue(21, parent=20),
        22: _fake_issue(22, labels=["priority:p2"]),
        23: _fake_issue(23, parent=22, state="closed", labels=["priority:p0"]),
    }

    def fake_fetcher(_repository, number, _token):
        return parents[number]

    assert resolve_effective_priority(
        "guillermomolina/protos",
        _fake_issue(30, parent=20),
        "fake",
        None,
        fetcher=fake_fetcher,
    ) == ("priority:p1", 20)
    assert resolve_effective_priority(
        "guillermomolina/protos",
        _fake_issue(31, labels=["priority:p3"], parent=20),
        "fake",
        "priority:p3",
        fetcher=fake_fetcher,
    ) == ("priority:p3", None)
    assert resolve_effective_priority(
        "guillermomolina/protos",
        _fake_issue(32, parent=21),
        "fake",
        None,
        fetcher=fake_fetcher,
    ) == ("priority:p1", 20)
    assert resolve_effective_priority(
        "guillermomolina/protos",
        _fake_issue(33, parent=23),
        "fake",
        None,
        fetcher=fake_fetcher,
    ) == ("priority:p2", 22)

    assert priority_migration_candidate(
        None, "P1", explicit_priority_removal=False
    ) == "priority:p1"
    assert priority_migration_candidate(
        "priority:p2", "P1", explicit_priority_removal=False
    ) is None
    assert priority_migration_candidate(
        None, "P1", explicit_priority_removal=True
    ) is None
    assert priority_migration_candidate(
        None, None, explicit_priority_removal=False
    ) is None
    try:
        priority_migration_candidate(
            None, "URGENT", explicit_priority_removal=False
        )
    except SyncError:
        pass
    else:
        raise AssertionError(
            "unknown Project Priority must fail closed during migration"
        )

    print("PROJECT_STATUS_PRIORITY_SYNC_SELF_TEST: PASS")
    print("UNKNOWN_STATUS_LABEL_FAIL_CLOSED: PASS")
    print("FORMAL_STATUS_FAIL_CLOSED_SELF_TEST: PASS")
    print("ACTIVE_ASSIGNEE_INVARIANT_SELF_TEST: PASS")
    print("NEEDS_DECISION_ASSIGNEE_INVARIANT_SELF_TEST: PASS")
    print("ACTIVE_PRIORITY_RESOLUTION_SELF_TEST: PASS")
    print("PARENT_PRIORITY_INHERITANCE_SELF_TEST: PASS")
    print("NONDESTRUCTIVE_PROJECT_PRIORITY_MIGRATION_SELF_TEST: PASS")


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

    item_map, project_issues, project_priorities = load_project_issue_items(
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
            project_priorities,
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
        project_priorities,
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
