#!/usr/bin/env python3
"""Stdlib-only reference helper for Protos generated PR-first patch launchers."""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ARTIFACT_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{5,63}$")
SLICE_RE = re.compile(r"^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*$")

class PatchPrError(RuntimeError):
    pass

def run(cmd, cwd=None, check=True):
    p = subprocess.run(cmd, cwd=str(cwd) if cwd else None, text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and p.returncode != 0:
        raise PatchPrError("command failed (%d): %s\n%s" %
                           (p.returncode, " ".join(cmd), p.stderr.strip()))
    return p

def slugify_slice(slice_id):
    if not SLICE_RE.fullmatch(slice_id):
        raise PatchPrError("invalid Protos slice identifier: %r" % slice_id)
    return slice_id.lower()

def validate_artifact_id(artifact_id):
    if not ARTIFACT_RE.fullmatch(artifact_id):
        raise PatchPrError("invalid artifact id: %r" % artifact_id)
    return artifact_id

def branch_name(slice_id, artifact_id):
    return "protos-patch/%s/%s" % (
        slugify_slice(slice_id), validate_artifact_id(artifact_id))

def body_marker(slice_id, artifact_id):
    return "<!-- protos-generated-patch artifact-id=%s slice=%s -->" % (
        validate_artifact_id(artifact_id), slice_id)

def commit_trailers(slice_id, artifact_id):
    return "Protos-Patch-Artifact: %s\nProtos-Patch-Slice: %s" % (
        validate_artifact_id(artifact_id), slice_id)

def body_owned(body, slice_id, artifact_id):
    return isinstance(body, str) and body_marker(
        slice_id, artifact_id) in body.splitlines()[:5]

def commit_owned(message, slice_id, artifact_id):
    lines = message.splitlines()
    return (
        "Protos-Patch-Artifact: %s" % artifact_id in lines
        and "Protos-Patch-Slice: %s" % slice_id in lines
    )

def result(state, branch, pr=None, remote_sha=None, merge_sha=None):
    return {
        "state": state,
        "head_branch": branch,
        "pr_url": pr.get("url") if isinstance(pr, dict) else None,
        "pr_number": pr.get("number") if isinstance(pr, dict) else None,
        "remote_sha": remote_sha,
        "merge_sha": merge_sha,
    }

def gh_prs(repo, branch, cwd):
    p = run([
        "gh", "pr", "list", "--repo", repo, "--head", branch, "--state", "all",
        "--limit", "20",
        "--json", "number,state,url,body,mergedAt,mergeCommit,baseRefName,headRefName",
    ], cwd)
    data = json.loads(p.stdout or "[]")
    if not isinstance(data, list):
        raise PatchPrError("unexpected gh pr list response")
    return [x for x in data if isinstance(x, dict)]

def remote_head_sha(cwd, branch):
    p = run(["git", "ls-remote", "--heads", "origin",
             "refs/heads/" + branch], cwd, check=False)
    if p.returncode not in (0, 2):
        raise PatchPrError("git ls-remote failed: " + p.stderr.strip())
    line = (p.stdout or "").strip()
    if not line:
        return None
    sha = line.split()[0]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise PatchPrError("unexpected remote branch SHA")
    return sha

def remote_commit_message(cwd, branch):
    remote_ref = "refs/remotes/origin/" + branch
    run(["git", "fetch", "--no-tags", "origin",
         "refs/heads/%s:%s" % (branch, remote_ref)], cwd)
    return run(["git", "show", "-s", "--format=%B", remote_ref], cwd).stdout

def inspect(cwd, repo, slice_id, artifact_id, base="main"):
    branch = branch_name(slice_id, artifact_id)
    prs = gh_prs(repo, branch, cwd)

    open_prs = [p for p in prs if p.get("state") == "OPEN"]
    if len(open_prs) > 1:
        raise PatchPrError("multiple open PRs use %s" % branch)
    if open_prs:
        p = open_prs[0]
        if p.get("baseRefName") != base or not body_owned(
                p.get("body"), slice_id, artifact_id):
            raise PatchPrError("existing open PR does not prove artifact ownership")
        return result("OPEN_PR", branch, p)

    merged = [
        p for p in prs
        if p.get("state") == "MERGED" and body_owned(
            p.get("body"), slice_id, artifact_id)
    ]
    if merged:
        p = sorted(merged, key=lambda x: x.get("number") or 0)[-1]
        mc = p.get("mergeCommit") or {}
        sha = mc.get("oid") if isinstance(mc, dict) else None
        if sha:
            run(["git", "fetch", "origin", base], cwd)
            a = run(["git", "merge-base", "--is-ancestor", sha, "FETCH_HEAD"],
                    cwd, check=False)
            if a.returncode == 0:
                return result("PUBLISHED", branch, p, merge_sha=sha)

    closed = [
        p for p in prs
        if p.get("state") == "CLOSED" and not p.get("mergedAt")
        and body_owned(p.get("body"), slice_id, artifact_id)
    ]
    if closed:
        p = sorted(closed, key=lambda x: x.get("number") or 0)[-1]
        return result("CLOSED_UNMERGED", branch, p)

    sha = remote_head_sha(cwd, branch)
    if sha is None:
        return result("ABSENT", branch)
    msg = remote_commit_message(cwd, branch)
    if not commit_owned(msg, slice_id, artifact_id):
        raise PatchPrError("remote branch exists but ownership trailers do not match")
    return result("RECOVERABLE_BRANCH", branch, remote_sha=sha)

def candidate_owned(cwd, sha, slice_id, artifact_id):
    msg = run(["git", "show", "-s", "--format=%B", sha], cwd).stdout
    if not commit_owned(msg, slice_id, artifact_id):
        raise PatchPrError("candidate commit lacks ownership trailers")

def create_pr(cwd, repo, branch, base, title, body_file):
    p = run([
        "gh", "pr", "create", "--repo", repo, "--base", base, "--head", branch,
        "--title", title, "--body-file", str(body_file),
    ], cwd)
    lines = [x.strip() for x in (p.stdout or "").splitlines() if x.strip()]
    if not lines or not lines[-1].startswith("https://github.com/"):
        raise PatchPrError("gh pr create did not return a PR URL")
    return lines[-1]

def publish(cwd, repo, slice_id, artifact_id, base, title, body_file,
            candidate_sha=None):
    current = inspect(cwd, repo, slice_id, artifact_id, base)
    if current["state"] == "OPEN_PR":
        return dict(current, result="PR_ALREADY_OPEN")
    if current["state"] == "PUBLISHED":
        return dict(current, result="PUBLISHED")
    if current["state"] == "CLOSED_UNMERGED":
        raise PatchPrError("owned PR was closed without merge; do not resurrect it")

    branch = current["head_branch"]
    created_remote = False
    pushed_sha = None

    if current["state"] == "ABSENT":
        if not candidate_sha:
            raise PatchPrError("candidate SHA required for absent branch")
        candidate_owned(cwd, candidate_sha, slice_id, artifact_id)
        p = run(["git", "push", "origin",
                 "%s:refs/heads/%s" % (candidate_sha, branch)], cwd, check=False)
        if p.returncode != 0:
            raise PatchPrError("non-force PR-head push failed: " + p.stderr.strip())
        created_remote = True
        pushed_sha = candidate_sha
    elif current["state"] == "RECOVERABLE_BRANCH":
        pushed_sha = current["remote_sha"]
    else:
        raise PatchPrError("unexpected state: %s" % current["state"])

    try:
        url = create_pr(cwd, repo, branch, base, title, body_file)
    except Exception:
        raced = inspect(cwd, repo, slice_id, artifact_id, base)
        if raced["state"] == "OPEN_PR":
            return dict(raced, result="PR_OPENED")
        if created_remote and pushed_sha and remote_head_sha(cwd, branch) == pushed_sha:
            cleanup = run(["git", "push", "origin", ":refs/heads/" + branch],
                          cwd, check=False)
            if cleanup.returncode != 0:
                print("REMOTE_BRANCH_CLEANUP_WARNING: " + cleanup.stderr.strip(),
                      file=sys.stderr)
        raise

    final = inspect(cwd, repo, slice_id, artifact_id, base)
    if final["state"] != "OPEN_PR" or final["pr_url"] != url:
        raise PatchPrError("PR creation postcondition failed")
    return dict(final, result=("PR_OPENED" if created_remote else "PR_RECOVERED"))

def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="command", required=True)
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--repo-root", required=True)
    common.add_argument("--repo", required=True)
    common.add_argument("--slice", required=True)
    common.add_argument("--artifact-id", required=True)
    common.add_argument("--base", default="main")
    sub.add_parser("inspect", parents=[common])
    pp = sub.add_parser("publish", parents=[common])
    pp.add_argument("--title", required=True)
    pp.add_argument("--body-file", required=True)
    pp.add_argument("--candidate-sha")
    ns = ap.parse_args()
    cwd = Path(ns.repo_root).resolve()
    try:
        if ns.command == "inspect":
            out = inspect(cwd, ns.repo, ns.slice, ns.artifact_id, ns.base)
        else:
            out = publish(cwd, ns.repo, ns.slice, ns.artifact_id, ns.base,
                          ns.title, Path(ns.body_file).resolve(), ns.candidate_sha)
        print(json.dumps(out, sort_keys=True))
        return 0
    except (PatchPrError, json.JSONDecodeError) as exc:
        print("PR_HELPER_ERROR: %s" % exc, file=sys.stderr)
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
