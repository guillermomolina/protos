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

API_VERSION = "2026-03-10"

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

WORK_QUEUE_VIEW_NAME = "Work queue"
WORK_QUEUE_FILTER = (
    'repo:guillermomolina/protos is:issue is:open '
    'status:"In progress",Review,Ready'
)


class SyncError(Exception):
    pass


def _json_request(url, token, method="GET", payload=None):
    data = None
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + token,
        "User-Agent": "protos-project-issue-sync",
        "X-GitHub-Api-Version": API_VERSION,
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
    allow_migration=True,
):
    if not allow_migration:
        return None
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


def unresolved_priority_action(
    current_project_priority,
    explicit_priority_removal=False,
    clear_stale_projection=False,
):
    if explicit_priority_removal or clear_stale_projection:
        if current_project_priority is None:
            return "unchanged"
        return "clear"
    if current_project_priority is None:
        return "unchanged"
    return "error"


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
      views(first: 100) {
        nodes {
          id
          name
          filter
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

ISSUE_PROJECT_ITEMS_QUERY = r"""
query($content: ID!, $after: String) {
  node(id: $content) {
    ... on Issue {
      projectItems(first: 100, after: $after) {
        nodes {
          id
          project {
            id
          }
          fieldValueByName(name: "Priority") {
            ... on ProjectV2ItemFieldSingleSelectValue {
              name
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

UPDATE_PROJECT_VIEW_MUTATION = r"""
mutation($view: ID!, $filter: String!) {
  updateProjectV2View(input: {viewId: $view, filter: $filter}) {
    projectV2View {
      id
      name
      filter
    }
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


def _normalized_project_view(node, name):
    if not node or node.get("name") != name:
        return None
    if not node.get("id"):
        raise SyncError("Project view %r has no node id" % name)
    return {
        "id": node["id"],
        "name": node.get("name"),
        "filter": node.get("filter") or "",
    }


def _named_project_view(project, name):
    matches = [
        _normalized_project_view(node, name)
        for node in ((project.get("views") or {}).get("nodes") or [])
        if node and node.get("name") == name
    ]
    matches = [node for node in matches if node is not None]
    if len(matches) > 1:
        raise SyncError(
            "Project contains duplicate %r views; found %d"
            % (name, len(matches))
        )
    return matches[0] if matches else None


def _project_view_probe_query(max_view_number):
    fields = []
    for number in range(1, int(max_view_number) + 1):
        fields.append(
            "      v%d: view(number: %d) { id name filter }"
            % (number, number)
        )
    return (
        "query($login: String!, $number: Int!) {\n"
        "  user(login: $login) {\n"
        "    projectV2(number: $number) {\n"
        + "\n".join(fields)
        + "\n    }\n  }\n}\n"
    )


def discover_project_view_by_number(
    project_token,
    owner,
    project_number,
    name,
    max_view_number=64,
    graphql_runner=None,
):
    if graphql_runner is None:
        graphql_runner = _graphql
    data = graphql_runner(
        project_token,
        _project_view_probe_query(max_view_number),
        {"login": owner, "number": int(project_number)},
    )
    project = (data.get("user") or {}).get("projectV2")
    if not project:
        raise SyncError("Cannot resolve Project while probing saved views")

    matches = []
    for key, node in project.items():
        if not key.startswith("v"):
            continue
        normalized = _normalized_project_view(node, name)
        if normalized is not None:
            matches.append(normalized)

    if len(matches) > 1:
        raise SyncError(
            "Project contains duplicate %r views during numbered probe"
            % name
        )
    return matches[0] if matches else None


def work_queue_filter_action(current_filter):
    if current_filter == WORK_QUEUE_FILTER:
        return "unchanged"
    return "update"


def reconcile_work_queue_view(project_token, project):
    view = project["work_queue"]
    if view is None:
        print(
            "PROJECT_VIEW_CONVERGENCE=EXPLICITLY_UNAVAILABLE "
            "Work queue not exposed by current Project token"
        )
        return None

    action = work_queue_filter_action(view.get("filter") or "")
    if action == "unchanged":
        print("PROJECT_VIEW_FILTER: Work queue -> canonical")
        return False

    data = _graphql(
        project_token,
        UPDATE_PROJECT_VIEW_MUTATION,
        {"view": view["id"], "filter": WORK_QUEUE_FILTER},
    )
    updated = (data.get("updateProjectV2View") or {}).get("projectV2View")
    if not updated or updated.get("id") != view["id"]:
        raise SyncError("Work queue view update returned no matching view")
    if (updated.get("filter") or "") != WORK_QUEUE_FILTER:
        raise SyncError(
            "Work queue view update did not converge to canonical filter"
        )

    project["work_queue"] = {
        "id": updated["id"],
        "name": updated.get("name") or WORK_QUEUE_VIEW_NAME,
        "filter": updated.get("filter") or "",
    }
    print("PROJECT_VIEW_FILTER: Work queue -> reconciled")
    return True


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

    work_queue = _named_project_view(project, WORK_QUEUE_VIEW_NAME)
    if work_queue is None:
        work_queue = discover_project_view_by_number(
            project_token,
            owner,
            number,
            WORK_QUEUE_VIEW_NAME,
        )

    return {
        "id": project["id"],
        "title": project.get("title"),
        "status": status_field,
        "priority": priority_field,
        "work_queue": work_queue,
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


def _matching_issue_project_item(project_id, nodes):
    matches = []
    for node in nodes:
        if not node:
            continue
        containing_project = node.get("project") or {}
        if containing_project.get("id") == project_id:
            matches.append(node)

    if len(matches) > 1:
        raise SyncError(
            "Issue is linked to Project %s more than once" % project_id
        )
    if not matches:
        return None, None

    item = matches[0]
    priority_value = item.get("fieldValueByName") or {}
    return item.get("id"), priority_value.get("name")


def load_issue_project_item(project_token, project, issue):
    nodes = []
    after = None
    while True:
        data = _graphql(
            project_token,
            ISSUE_PROJECT_ITEMS_QUERY,
            {"content": issue["node_id"], "after": after},
        )
        node = data.get("node")
        if node is None:
            raise SyncError(
                "Cannot resolve Issue #%s GraphQL node %s"
                % (issue.get("number"), issue.get("node_id"))
            )
        connection = node.get("projectItems")
        if connection is None:
            raise SyncError(
                "Issue #%s GraphQL response omitted projectItems"
                % issue.get("number")
            )

        nodes.extend(connection.get("nodes") or [])
        page = connection.get("pageInfo") or {}
        if not page.get("hasNextPage"):
            break
        after = page.get("endCursor")
        if not after:
            raise SyncError(
                "Issue #%s Project item pagination lost its end cursor"
                % issue.get("number")
            )

    return _matching_issue_project_item(project["id"], nodes)


def prepare_bounded_project_context(
    project_token,
    project,
    repository,
    issue,
    item_map,
    project_priorities,
):
    key = (repository, int(issue["number"]))
    item_id, priority_name = load_issue_project_item(
        project_token, project, issue
    )
    if item_id is not None:
        item_map[key] = item_id
    project_priorities[key] = priority_name


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


def list_direct_subissues(repository, number, issue_token):
    page = 1
    while True:
        batch = _rest(
            repository,
            "/issues/%d/sub_issues?per_page=100&page=%d"
            % (int(number), page),
            issue_token,
        )
        if not batch:
            return
        for issue in batch:
            if issue.get("pull_request"):
                raise SyncError(
                    "Issue #%d native sub-issue graph contains pull request #%s"
                    % (int(number), issue.get("number"))
                )
            yield issue
        if len(batch) < 100:
            return
        page += 1


def discover_descendant_numbers(
    repository,
    root_number,
    issue_token,
    child_lister=None,
):
    if child_lister is None:
        child_lister = list_direct_subissues

    root_number = int(root_number)
    seen = set([root_number])
    queue = [root_number]
    descendants = []

    while queue:
        parent_number = queue.pop(0)
        children = list(
            child_lister(repository, parent_number, issue_token)
        )
        children.sort(key=lambda issue: int(issue["number"]))
        for child in children:
            child_number = int(child["number"])
            if child_number in seen:
                raise SyncError(
                    "Native sub-issue graph rooted at #%d repeats/cycles at #%d"
                    % (root_number, child_number)
                )
            seen.add(child_number)
            descendants.append(child_number)
            queue.append(child_number)

    return descendants


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
    priority_fetcher=None,
    allow_project_priority_migration=True,
    clear_unresolved_project_priority=False,
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
        repository,
        issue,
        issue_token,
        canonical_priority,
        fetcher=priority_fetcher,
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
        allow_migration=allow_project_priority_migration,
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
        action = unresolved_priority_action(
            current_project_priority,
            explicit_priority_removal=explicit_priority_removal,
            clear_stale_projection=clear_unresolved_project_priority,
        )
        if action == "clear":
            clear_project_field(project_token, project, item_id, "priority")
            project_priorities[key] = None
            if explicit_priority_removal:
                reason = "explicit priority label removal"
            else:
                reason = "bounded inherited-priority recomputation"
            print(
                "PROJECT_PRIORITY: #%d -> <unset> (%s)"
                % (number, reason)
            )
        elif action == "unchanged":
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


def run_descendant_reconcile(
    args,
    root_issue,
    project_token,
    issue_token,
    project,
    item_map,
    project_priorities,
):
    root_number = int(root_issue["number"])
    descendants = discover_descendant_numbers(
        args.repository,
        root_number,
        issue_token,
    )

    issue_cache = {root_number: root_issue}

    def cached_fetcher(repository, number, token):
        number = int(number)
        cached = issue_cache.get(number)
        if cached is None:
            cached = fetch_issue(repository, number, token)
            issue_cache[number] = cached
        return cached

    reconciled = 0
    closed_traversed = 0
    for number in descendants:
        issue = cached_fetcher(args.repository, number, issue_token)
        if issue.get("pull_request"):
            raise SyncError(
                "Native descendant #%d unexpectedly resolves to a pull request"
                % number
            )
        if issue.get("state") != "open":
            closed_traversed += 1
            continue

        prepare_bounded_project_context(
            project_token,
            project,
            args.repository,
            issue,
            item_map,
            project_priorities,
        )
        sync_issue(
            args.repository,
            issue,
            project_token,
            issue_token,
            project,
            item_map,
            project_priorities,
            priority_fetcher=cached_fetcher,
            allow_project_priority_migration=False,
            clear_unresolved_project_priority=True,
        )
        reconciled += 1

    print("RECONCILE_ROOT=#%d" % root_number)
    print("DESCENDANTS_DISCOVERED=%d" % len(descendants))
    print("OPEN_DESCENDANTS_RECONCILED=%d" % reconciled)
    print("CLOSED_DESCENDANTS_TRAVERSED=%d" % closed_traversed)
    print("UNRELATED_ISSUES_RECONCILED=0")
    print("FULL_RECONCILIATION=NO")


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

    assert priority_migration_candidate(
        None,
        "P1",
        explicit_priority_removal=False,
        allow_migration=False,
    ) is None

    assert unresolved_priority_action(
        "P1",
        explicit_priority_removal=False,
        clear_stale_projection=True,
    ) == "clear"
    assert unresolved_priority_action(
        None,
        explicit_priority_removal=False,
        clear_stale_projection=True,
    ) == "unchanged"
    assert unresolved_priority_action(
        "P1",
        explicit_priority_removal=False,
        clear_stale_projection=False,
    ) == "error"

    project_nodes = [
        {
            "id": "PVTI_other",
            "project": {"id": "PVT_other"},
            "fieldValueByName": {"name": "P0"},
        },
        {
            "id": "PVTI_target",
            "project": {"id": "PVT_target"},
            "fieldValueByName": {"name": "P2"},
        },
    ]
    assert _matching_issue_project_item(
        "PVT_target", project_nodes
    ) == ("PVTI_target", "P2")
    assert _matching_issue_project_item(
        "PVT_missing", project_nodes
    ) == (None, None)
    try:
        _matching_issue_project_item(
            "PVT_target",
            project_nodes + [{
                "id": "PVTI_duplicate",
                "project": {"id": "PVT_target"},
                "fieldValueByName": None,
            }],
        )
    except SyncError:
        pass
    else:
        raise AssertionError("duplicate Project membership must fail closed")

    graph = {
        100: [_fake_issue(102), _fake_issue(101)],
        101: [_fake_issue(103)],
        102: [_fake_issue(104)],
        103: [],
        104: [_fake_issue(105)],
        105: [],
        999: [_fake_issue(998)],
    }
    visited_parents = []

    def fake_child_lister(_repository, number, _token):
        visited_parents.append(number)
        return list(graph.get(number, []))

    assert discover_descendant_numbers(
        "guillermomolina/protos",
        100,
        "fake",
        child_lister=fake_child_lister,
    ) == [101, 102, 103, 104, 105]
    assert 999 not in visited_parents
    assert 998 not in visited_parents

    cycle_graph = {
        200: [_fake_issue(201)],
        201: [_fake_issue(200)],
    }

    def cycle_child_lister(_repository, number, _token):
        return list(cycle_graph.get(number, []))

    try:
        discover_descendant_numbers(
            "guillermomolina/protos",
            200,
            "fake",
            child_lister=cycle_child_lister,
        )
    except SyncError as exc:
        assert "repeats/cycles" in str(exc)
    else:
        raise AssertionError("native descendant cycle must fail closed")

    assert "no:parent-issue" not in WORK_QUEUE_FILTER
    assert 'status:"In progress",Review,Ready' in WORK_QUEUE_FILTER
    assert work_queue_filter_action(WORK_QUEUE_FILTER) == "unchanged"
    assert work_queue_filter_action(
        WORK_QUEUE_FILTER + " no:parent-issue"
    ) == "update"

    project_with_views = {
        "views": {
            "nodes": [
                {"id": "PVTV_other", "name": "Decisions", "filter": "x"},
                {
                    "id": "PVTV_work",
                    "name": WORK_QUEUE_VIEW_NAME,
                    "filter": WORK_QUEUE_FILTER,
                },
            ]
        }
    }
    assert _named_project_view(
        project_with_views, WORK_QUEUE_VIEW_NAME
    ) == {
        "id": "PVTV_work",
        "name": WORK_QUEUE_VIEW_NAME,
        "filter": WORK_QUEUE_FILTER,
    }
    assert _named_project_view(
        {"views": {"nodes": []}},
        WORK_QUEUE_VIEW_NAME,
    ) is None

    try:
        _named_project_view(
            {
                "views": {
                    "nodes": [
                        {"id": "a", "name": WORK_QUEUE_VIEW_NAME, "filter": ""},
                        {"id": "b", "name": WORK_QUEUE_VIEW_NAME, "filter": ""},
                    ]
                }
            },
            WORK_QUEUE_VIEW_NAME,
        )
    except SyncError:
        pass
    else:
        raise AssertionError(
            "duplicate Work queue views must fail closed"
        )

    probe_query = _project_view_probe_query(3)
    assert "v1: view(number: 1)" in probe_query
    assert "v3: view(number: 3)" in probe_query

    def fake_view_probe(_token, _query, variables):
        assert variables == {"login": "owner", "number": 7}
        return {
            "user": {
                "projectV2": {
                    "v1": {
                        "id": "other",
                        "name": "Decisions",
                        "filter": "x",
                    },
                    "v2": {
                        "id": "work",
                        "name": WORK_QUEUE_VIEW_NAME,
                        "filter": WORK_QUEUE_FILTER,
                    },
                    "v3": None,
                }
            }
        }

    assert discover_project_view_by_number(
        "token",
        "owner",
        7,
        WORK_QUEUE_VIEW_NAME,
        max_view_number=3,
        graphql_runner=fake_view_probe,
    ) == {
        "id": "work",
        "name": WORK_QUEUE_VIEW_NAME,
        "filter": WORK_QUEUE_FILTER,
    }

    def fake_no_view(_token, _query, _variables):
        return {"user": {"projectV2": {"v1": None, "v2": None}}}

    assert discover_project_view_by_number(
        "token",
        "owner",
        7,
        WORK_QUEUE_VIEW_NAME,
        max_view_number=2,
        graphql_runner=fake_no_view,
    ) is None

    print("BOUNDED_DESCENDANT_TRAVERSAL_SELF_TEST: PASS")
    print("BOUNDED_PROJECT_ITEM_LOOKUP_SELF_TEST: PASS")
    print("STALE_INHERITED_PRIORITY_CLEAR_SELF_TEST: PASS")
    print("WORK_QUEUE_HIERARCHY_FILTER_SELF_TEST: PASS")
    print("WORK_QUEUE_VIEW_DRIFT_SELF_TEST: PASS")

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
    parser.add_argument("--reconcile-descendants", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    if args.self_test:
        self_test()
        return 0
    if not args.repository:
        raise SyncError("--repository or GITHUB_REPOSITORY is required")
    if args.reconcile_all:
        if args.issue_number is not None or args.reconcile_descendants:
            raise SyncError(
                "--reconcile-all cannot be combined with Issue-scoped modes"
            )
    elif args.issue_number is None:
        raise SyncError(
            "--issue-number is required unless --reconcile-all is selected"
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
    reconcile_work_queue_view(project_token, project)

    if args.reconcile_all:
        item_map, project_issues, project_priorities = load_project_issue_items(
            project_token, args.project_owner, args.project_number
        )
        run_reconcile(
            args,
            project_token,
            issue_token,
            project,
            item_map,
            project_issues,
            project_priorities,
        )
        print("FULL_RECONCILIATION=YES")
        return 0

    # Routine Issue-scoped operation must not enumerate all Project items.
    item_map = {}
    project_priorities = {}
    issue = fetch_issue(
        args.repository, args.issue_number, issue_token
    )

    if args.reconcile_descendants:
        run_descendant_reconcile(
            args,
            issue,
            project_token,
            issue_token,
            project,
            item_map,
            project_priorities,
        )
        return 0

    prepare_bounded_project_context(
        project_token,
        project,
        args.repository,
        issue,
        item_map,
        project_priorities,
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
    print("RECONCILE_ROOT=#%d" % int(issue["number"]))
    print("ISSUES_RECONCILED=1")
    print("UNRELATED_ISSUES_RECONCILED=0")
    print("FULL_RECONCILIATION=NO")
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
