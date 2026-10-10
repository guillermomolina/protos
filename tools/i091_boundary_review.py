#!/usr/bin/env python3
# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED AT:
# https://github.com/guillermomolina/protos
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

"""I091-A source-grounded boundary queue and Candidate C static preflight.

Boundary labels identify review ownership, not architectural conclusions.
No reference is considered removable by this tool.
"""

import csv
import json
from collections import Counter
from pathlib import Path

import numeric_dependency_census as census


ROOT = Path("src/main/java/com/guillermomolina/protos")
TARGET = Path("target")


def review_boundary(path):
    name = Path(path).stem

    numeric = {
        "ProtosIntegerValue",
        "ProtosLargeIntegerValue",
        "ProtosFloatValue",
        "ProtosNumberLiteral",
        "ProtosNumericValueSupport",
        "ProtosBinary64Rounding",
        "ProtosStandardIntegerProtocol",
        "ProtosStandardFloatProtocol",
        "ProtosStandardNumericConversionProtocol",
    }

    identity = {
        "ProtosIdentity",
        "ProtosCurrentNumericRelations",
        "ProtosStandardHashSupport",
        "ProtosStandardNumberEqualityProtocol",
        "ProtosStandardNumberOrderingProtocol",
        "ProtosValueLookup",
    }

    collections = {
        "ProtosArrayValue",
        "ProtosMapValue",
        "ProtosIdentityMapValue",
        "ProtosBytesValue",
        "ProtosStandardArrayProtocol",
        "ProtosStandardMapProtocol",
        "ProtosStandardBytesProtocol",
    }

    transfers = {
        "ProtosActorValueTransfer",
        "ProtosParallelRuntime",
        "ProtosDetachedExecutionValue",
        "ProtosSemanticTransferPayload",
        "ProtosSemanticTransferRecord",
    }

    if name in numeric:
        return "NUMERIC_ALGORITHMS_AND_CARRIERS"

    if name in identity:
        return "SEMANTIC_IDENTITY_EQUALITY_HASH"

    if name in collections:
        return "COLLECTIONS_AND_INDEXING"

    if name in transfers or "Transfer" in name:
        return "ISOLATION_AND_TRANSFER"

    if "BytecodeRootNode" in name:
        return "BYTECODE_EXECUTION"

    if "Foreign" in name or "Interop" in name:
        return "FOREIGN_AND_HOST_INTEROP"

    if any(token in name for token in (
        "File", "Path", "ByteIo", "TextReader",
        "Network", "Tcp", "IpAddress", "IpEndpoint",
        "Encoding", "Nio",
    )):
        return "IO_AND_NETWORK"

    if any(token in name for token in (
        "TestTool", "Cli", "Renderer", "Diagnostic",
        "Package", "Logging", "Regex",
    )):
        return "TOOLS_AND_LIBRARY_SUPPORT"

    return "MANUAL_OWNERSHIP_REVIEW"


def source_check(check_id, relative_path, needle, interpretation):
    path = ROOT / relative_path
    text = path.read_text(encoding="utf-8")
    observed = needle in text

    return {
        "id": check_id,
        "path": path.as_posix(),
        "observation": needle,
        "source_match": observed,
        "interpretation_if_found": interpretation,
        "status": "OBSERVED" if observed else "REVIEW_SOURCE_DRIFT",
    }


CHECKS = [
    (
        "F1_FROZEN_OBJECT",
        "runtime/ProtosObjectValue.java",
        "public ProtosObjectValue freeze()",
        "Ordinary guest objects can become frozen",
    ),
    (
        "F1_PRIVATE_IMMUTABLE_STATE",
        "runtime/ProtosSemanticTransferValue.java",
        "private final Object familyState",
        "An existing ordinary-object-derived value stores private state",
    ),
    (
        "F1_TRANSFER_FAMILY_STD_RESTRICTION",
        "runtime/ProtosSemanticTransferFamily.java",
        "startsWith(STANDARD_LIBRARY_PREFIX)",
        "Existing transfer-family admission is restricted to std: modules",
    ),
    (
        "F1_SEALED_ACTOR_REJECTION",
        "runtime/ProtosActorValueTransfer.java",
        "|| value instanceof ProtosSealedValue",
        "Sealed values cannot currently cross Actor isolation",
    ),
    (
        "F1_SEMANTIC_ACTOR_TRANSFER",
        "runtime/ProtosActorValueTransfer.java",
        "object instanceof ProtosSemanticTransferValue",
        "Actor transfer already supports semantic records for authorized families",
    ),
    (
        "F1_SEMANTIC_PARALLEL_TRANSFER",
        "execution/ProtosParallelRuntime.java",
        "v instanceof ProtosSemanticTransferValue",
        "P transfer also has semantic-family handling",
    ),
    (
        "F2_BYTECODE_ROOT_TYPED_CARRIERS",
        "execution/ProtosBytecodeRootNode.java",
        "boxingEliminationTypes = {int.class, long.class, double.class}",
        "The root enables long/double boxing elimination; the complete guest numeric chain remains unproven",
    ),
    (
        "F2_SEMANTIC_ROOT_TYPED_CARRIERS",
        "execution/ProtosSemanticBytecodeRootNode.java",
        "boxingEliminationTypes = {int.class, long.class, double.class}",
        "The root enables long/double boxing elimination; the complete guest numeric chain remains unproven",
    ),
    (
        "F2_INTEGER_WRAPPER_RESULT",
        "runtime/ProtosIntegerValue.java",
        "new ProtosIntegerValue(Math.addExact",
        "Current ordinary exact addition still constructs a wrapper",
    ),
    (
        "F4_NUMBER_EQUALITY_CARRIER_DEPENDENCY",
        "runtime/ProtosNumericValueSupport.java",
        "return isCurrentInteger(value) || isCurrentFloat(value);",
        "Current numeric recognition is centralized; rich families remain pending",
    ),
    (
        "F4_IDENTITY_WRAPPER_DEPENDENCY",
        "runtime/ProtosIdentity.java",
        "ProtosNumericValueSupport.sameCurrentFamilyIdentity(left,right)",
        "Identity delegates to the numeric boundary; rich identity remains pending",
    ),
    (
        "F4_HASH_WRAPPER_DEPENDENCY",
        "execution/ProtosCurrentNumericRelations.java",
        "static BigInteger normalHash(Object value)",
        "Current numeric hash is centralized; rich-family hashing remains pending",
    ),
]


def main():
    prior_path = TARGET / "i091-numeric-census.json"

    if not prior_path.is_file():
        raise RuntimeError("Run numeric_dependency_census.py first")

    prior = json.loads(prior_path.read_text(encoding="utf-8"))
    current = census.inventory()

    for key in (
        "head",
        "production_source_fingerprint",
        "production_java_files",
        "matching_java_files",
        "total_sites",
        "symbol_counts",
    ):
        if prior[key] != current[key]:
            raise RuntimeError(
                f"Stale census or concurrent source modification: {key}"
            )

    if len(current["sites"]) != current["total_sites"]:
        raise RuntimeError("Inconsistent site total")

    files = []
    counts = Counter()

    for item in current["files"]:
        boundary = review_boundary(item["path"])
        counts[boundary] += item["total"]

        files.append({
            "path": item["path"],
            "module": item["module"],
            "review_boundary": boundary,
            "BigInteger": item["counts"].get("BigInteger", 0),
            "ProtosIntegerValue": item["counts"].get(
                "ProtosIntegerValue", 0
            ),
            "ProtosFloatValue": item["counts"].get(
                "ProtosFloatValue", 0
            ),
            "references": item["total"],
            "status": "SOURCE_REVIEW_REQUIRED",
        })

    checks = [
        source_check(*specification)
        for specification in CHECKS
    ]

    if sum(counts.values()) != current["total_sites"]:
        raise RuntimeError("Review queue lost numeric references")

    report = {
        "schema": "I091-CANDIDATE-C-PREFLIGHT-1",
        "head": current["head"],
        "fingerprint": current["production_source_fingerprint"],
        "baseline_references": current["total_sites"],
        "boundary_counts": dict(sorted(counts.items())),
        "checks": checks,
        "decision": "FEASIBILITY_NOT_YET_PROVEN",
        "pending": [
            "Value identity and numeric hash for separate rich-object allocations",
            "Core numeric transfer with existing std:-restricted transfer families",
            "Primitive long/double through both roots and call/return/local paths",
            "Current-semantic D013 override and deoptimization equivalence",
            "Complete manually justified classification of all source references",
            "Actor/P/Process and multi-Context numeric value integration",
            "D197 public Integer recognition approval before I090 changes",
        ],
    }

    TARGET.mkdir(parents=True, exist_ok=True)

    output = TARGET / "i091-boundary-review.tsv"
    with output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=list(files[0]),
            delimiter="\t",
        )
        writer.writeheader()
        writer.writerows(files)

    preflight = TARGET / "i091-feasibility-preflight.json"
    preflight.write_text(
        json.dumps(report, indent=2) + "\n",
        encoding="utf-8",
    )

    print("I091-A BOUNDARY REVIEW")
    print(f"HEAD: {report['head']}")
    print(f"Java files requiring review: {len(files)}")
    print(f"References preserved: {sum(counts.values())}")

    for boundary, count in sorted(
        counts.items(),
        key=lambda entry: (-entry[1], entry[0]),
    ):
        print(f"  {boundary}: {count}")

    print("SOURCE FEASIBILITY OBSERVATIONS")
    for item in checks:
        print(f"  {item['id']}: {item['status']}")

    print("Top 15 files by numeric references:")
    for item in sorted(
        files,
        key=lambda record: (-record["references"], record["path"]),
    )[:15]:
        print(
            f"  {item['references']:3d} "
            f"{item['review_boundary']} {item['path']}"
        )

    print(f"REVIEW QUEUE: {output}")
    print(f"PREFLIGHT: {preflight}")
    print("CANDIDATE C FEASIBILITY: NOT YET PROVEN")
    print("ALL SITE CLASSIFICATIONS: STILL UNCLASSIFIED")


if __name__ == "__main__":
    main()
