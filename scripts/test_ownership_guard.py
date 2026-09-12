# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE.
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

# Validate explicit primary ownership of migrated public semantic tests.

from __future__ import print_function

import argparse
import json
from pathlib import Path, PurePosixPath
import re
import sys


REGISTRY_PATH = Path("protos/tests/test_ownership.json")
CONTRACT_ID = re.compile(r"^[a-z0-9]+(?:[.-][a-z0-9]+)*$")
CLASSIFICATIONS = frozenset(("PROTOS_SEMANTIC", "STANDARD_LIBRARY"))
STATES = frozenset(("RECONCILED", "MIGRATION_OVERLAP", "EXCEPTION"))
PRIMARY_OWNERS = frozenset(("TOOL002", "JUNIT"))
SECONDARY_OWNERS = frozenset(("JUNIT", "SHELL"))
SECONDARY_ROLES = frozenset((
    "HOST_RUNTIME",
    "INTEGRATION_BOOTSTRAP",
    "MIGRATION_OVERLAP",
))


class TestOwnershipGuardError(Exception):
    pass


def _meaningful(value, minimum=1):
    return isinstance(value, str) and len(value.strip()) >= minimum


def _exact_repo_path(repo, value, label):
    if not isinstance(value, str) or not value:
        raise TestOwnershipGuardError(label + " must be a non-empty repository path")
    if "\\" in value:
        raise TestOwnershipGuardError(label + " must use '/' separators")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or "." in path.parts:
        raise TestOwnershipGuardError(label + " must be an exact repository-relative path")
    concrete = repo.joinpath(*path.parts)
    if not concrete.is_file():
        raise TestOwnershipGuardError(label + " does not name an existing file: " + value)
    return value


def _evidence(repo, value, label):
    if not isinstance(value, list) or not value:
        raise TestOwnershipGuardError(label + " must be a non-empty list")
    normalized = []
    for index, path in enumerate(value):
        exact = _exact_repo_path(repo, path, "%s[%d]" % (label, index))
        if exact in normalized:
            raise TestOwnershipGuardError(label + " contains duplicate evidence path: " + exact)
        normalized.append(exact)
    return tuple(normalized)


def _primary(repo, contract_id, value):
    label = contract_id + ".primary"
    if not isinstance(value, dict):
        raise TestOwnershipGuardError(label + " must be an object")
    allowed = frozenset(("owner", "evidence"))
    unknown = sorted(set(value) - allowed)
    if unknown:
        raise TestOwnershipGuardError(label + " has unknown fields: " + ",".join(unknown))
    owner = value.get("owner")
    if owner not in PRIMARY_OWNERS:
        raise TestOwnershipGuardError(label + " has unsupported owner: " + str(owner))
    evidence = _evidence(repo, value.get("evidence"), label + ".evidence")
    return owner, evidence


def _secondary(repo, contract_id, index, value):
    label = "%s.secondary[%d]" % (contract_id, index)
    if not isinstance(value, dict):
        raise TestOwnershipGuardError(label + " must be an object")
    allowed = frozenset(("owner", "role", "evidence", "reason"))
    unknown = sorted(set(value) - allowed)
    if unknown:
        raise TestOwnershipGuardError(label + " has unknown fields: " + ",".join(unknown))
    owner = value.get("owner")
    role = value.get("role")
    if owner not in SECONDARY_OWNERS:
        raise TestOwnershipGuardError(label + " has unsupported owner: " + str(owner))
    if role not in SECONDARY_ROLES:
        raise TestOwnershipGuardError(label + " has unsupported role: " + str(role))
    evidence = _evidence(repo, value.get("evidence"), label + ".evidence")
    if not _meaningful(value.get("reason"), 15):
        raise TestOwnershipGuardError(label + ".reason must explain the distinct retained property")
    return {
        "owner": owner,
        "role": role,
        "evidence": evidence,
        "reason": value["reason"].strip(),
    }


def validate(repo):
    repo = Path(repo).resolve()
    registry = repo / REGISTRY_PATH
    if not registry.is_file():
        raise TestOwnershipGuardError("ownership registry is missing: " + str(REGISTRY_PATH))

    try:
        data = json.loads(registry.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise TestOwnershipGuardError("ownership registry is not valid JSON") from exc

    if not isinstance(data, dict):
        raise TestOwnershipGuardError("ownership registry must be a JSON object")
    if set(data) != set(("version", "contracts")):
        raise TestOwnershipGuardError("ownership registry must contain exactly version and contracts")
    if data.get("version") != 1:
        raise TestOwnershipGuardError("ownership registry version must be 1")

    contracts = data.get("contracts")
    if not isinstance(contracts, list):
        raise TestOwnershipGuardError("ownership registry contracts must be a list")

    ids = []
    reconciled = 0
    overlaps = 0
    exceptions = 0

    for index, contract in enumerate(contracts):
        label = "contracts[%d]" % index
        if not isinstance(contract, dict):
            raise TestOwnershipGuardError(label + " must be an object")

        allowed = frozenset((
            "id",
            "classification",
            "state",
            "primary",
            "secondary",
            "migration_ref",
            "exception_reason",
        ))
        unknown = sorted(set(contract) - allowed)
        if unknown:
            raise TestOwnershipGuardError(label + " has unknown fields: " + ",".join(unknown))

        contract_id = contract.get("id")
        if not isinstance(contract_id, str) or CONTRACT_ID.fullmatch(contract_id) is None:
            raise TestOwnershipGuardError(label + ".id must be a canonical lowercase contract key")
        if contract_id in ids:
            raise TestOwnershipGuardError("duplicate semantic contract id: " + contract_id)
        ids.append(contract_id)

        classification = contract.get("classification")
        if classification not in CLASSIFICATIONS:
            raise TestOwnershipGuardError(
                contract_id + " has unsupported classification: " + str(classification)
            )

        state = contract.get("state")
        if state not in STATES:
            raise TestOwnershipGuardError(contract_id + " has unsupported state: " + str(state))

        if not _meaningful(contract.get("migration_ref"), 8):
            raise TestOwnershipGuardError(contract_id + ".migration_ref must identify its TEST work")

        primary_owner, primary_evidence = _primary(repo, contract_id, contract.get("primary"))

        secondary_raw = contract.get("secondary", [])
        if not isinstance(secondary_raw, list):
            raise TestOwnershipGuardError(contract_id + ".secondary must be a list")
        secondary = [
            _secondary(repo, contract_id, i, value)
            for i, value in enumerate(secondary_raw)
        ]

        secondary_evidence = set()
        for item in secondary:
            for path in item["evidence"]:
                if path in primary_evidence:
                    raise TestOwnershipGuardError(
                        contract_id + " uses the same evidence as primary and secondary: " + path
                    )
                if path in secondary_evidence:
                    raise TestOwnershipGuardError(
                        contract_id + " repeats secondary evidence path: " + path
                    )
                secondary_evidence.add(path)

        migration_overlap = [item for item in secondary if item["role"] == "MIGRATION_OVERLAP"]

        if state == "RECONCILED":
            reconciled += 1
            if primary_owner != "TOOL002":
                raise TestOwnershipGuardError(
                    contract_id + " RECONCILED public semantics must have TOOL002 as primary owner"
                )
            if migration_overlap:
                raise TestOwnershipGuardError(
                    contract_id + " RECONCILED contract cannot retain MIGRATION_OVERLAP"
                )
            if "exception_reason" in contract:
                raise TestOwnershipGuardError(
                    contract_id + " RECONCILED contract cannot declare exception_reason"
                )

        elif state == "MIGRATION_OVERLAP":
            overlaps += 1
            if primary_owner != "TOOL002":
                raise TestOwnershipGuardError(
                    contract_id + " MIGRATION_OVERLAP requires TOOL002 as selected primary owner"
                )
            if not migration_overlap:
                raise TestOwnershipGuardError(
                    contract_id + " MIGRATION_OVERLAP requires explicit secondary overlap evidence"
                )
            if "exception_reason" in contract:
                raise TestOwnershipGuardError(
                    contract_id + " MIGRATION_OVERLAP cannot declare exception_reason"
                )

        else:
            exceptions += 1
            if primary_owner != "JUNIT":
                raise TestOwnershipGuardError(
                    contract_id + " EXCEPTION requires an explicit JUNIT primary owner"
                )
            if migration_overlap:
                raise TestOwnershipGuardError(
                    contract_id + " EXCEPTION cannot retain MIGRATION_OVERLAP"
                )
            if not _meaningful(contract.get("exception_reason"), 20):
                raise TestOwnershipGuardError(
                    contract_id + ".exception_reason must explain why TOOL002 is not appropriate"
                )

    if ids != sorted(ids):
        raise TestOwnershipGuardError("ownership registry contracts must be sorted by id")

    return {
        "contracts": len(contracts),
        "reconciled": reconciled,
        "migration_overlap": overlaps,
        "exceptions": exceptions,
    }


def run(repo):
    try:
        result = validate(repo)
    except TestOwnershipGuardError as exc:
        print("TEST_OWNERSHIP_GUARD: FAIL_CLOSED", file=sys.stderr)
        print("TEST_OWNERSHIP_GUARD_ERROR: " + str(exc), file=sys.stderr)
        return 2

    print("TEST_OWNERSHIP_CONTRACTS=%d" % result["contracts"])
    print("TEST_OWNERSHIP_RECONCILED=%d" % result["reconciled"])
    print("TEST_OWNERSHIP_MIGRATION_OVERLAP=%d" % result["migration_overlap"])
    print("TEST_OWNERSHIP_EXCEPTIONS=%d" % result["exceptions"])
    print("TEST_OWNERSHIP_GUARD: PASS")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    args = parser.parse_args(argv)
    return run(args.repo)


if __name__ == "__main__":
    sys.exit(main())
