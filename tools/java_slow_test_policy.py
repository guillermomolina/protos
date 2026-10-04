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


"""TEST008/PLAT047 Java slow-test admission policy (pure, deterministic).

This module holds only data parsing and decision math; it never measures,
sleeps, or runs anything, so every rule is testable with synthetic values.
Measurement and orchestration live in java_slow_test_guard.py.

PLAT047 model (Candidate H):

- Two Protos-independent controls (CPU/JVM and filesystem/process) are
  measured twice per run (before and after the Java phases), and a tiny
  probe is sampled during them and compared with the reviewed normal test
  load; drift of either marks transient contention,
  which is reported as WARN instead of judged as a regression. Each control's
  factor is observed/reference using the smaller sample; a common machine
  factor exists only when every factor lies in the supported domain and the
  factors agree within the coherence limit. Otherwise the run is
  ENVIRONMENT_NOT_COMPARABLE (ERROR), never a raw-time fallback.
- Raw parallel Surefire class time is suspicion evidence only. Every
  suspect receives exactly one reduced-contention confirmation, and both
  observations are kept: confirmation decides isolated cost, the
  parallel/confirmation ratio decides parallel interaction.
- The global signal is the real Java phase makespan divided by the machine
  factor, compared to a reviewed global expectation; it never uses the
  suite's own timings as its denominator.
- The pathological ceiling compares raw seconds after execution only.

Every policy constant and every expectation comes from the reviewed
baseline file; nothing here is learned from a run.
"""

from __future__ import print_function

import math
from pathlib import Path
from typing import Dict, List, NamedTuple, Optional, Tuple

CONTROLS = ("cpu_jvm", "fs_process")

POLICY_CONSTANTS = (
    "CONTROL_FACTOR_MIN",
    "CONTROL_FACTOR_MAX",
    "CONTROL_COHERENCE_LIMIT",
    "CLASS_REGRESSION_FACTOR",
    "GLOBAL_REGRESSION_FACTOR",
    "PARALLEL_INTERACTION_LIMIT",
    "PATHOLOGICAL_CEILING_SECONDS",
)

POLICY_STATUSES = ("PENDING", "UNAPPROVED_CANDIDATE_VALUES", "APPROVED")

# The approved TEST008 ordinary threshold, now applied to machine-normalized
# time: a class above it is a suspect that must be confirmed.
SUSPICION_SECONDS = 10.0

PENDING = "PENDING"

# Outcome classifications.
NORMAL = "NORMAL"
CONFIRMED_NORMAL = "CONFIRMED_NORMAL"
TRANSIENT_CONTENTION = "TRANSIENT_CONTENTION"
ISOLATED_TEST_REGRESSION = "ISOLATED_TEST_REGRESSION"
PARALLEL_INTERACTION_REGRESSION = "PARALLEL_INTERACTION_REGRESSION"
NEW_UNBASELINED_EXPENSIVE_TEST = "NEW_UNBASELINED_EXPENSIVE_TEST"
PATHOLOGICAL_COST = "PATHOLOGICAL_COST"
GLOBAL_RUNTIME_REGRESSION = "GLOBAL_RUNTIME_REGRESSION"
ENVIRONMENT_NOT_COMPARABLE = "ENVIRONMENT_NOT_COMPARABLE"
BASELINE_PENDING = "BASELINE_PENDING"
CONFIGURATION_ERROR = "CONFIGURATION_ERROR"

FAILING = frozenset([ISOLATED_TEST_REGRESSION, PARALLEL_INTERACTION_REGRESSION,
                     NEW_UNBASELINED_EXPENSIVE_TEST, PATHOLOGICAL_COST,
                     GLOBAL_RUNTIME_REGRESSION])


class PolicyError(Exception):
    pass


Baseline = NamedTuple("Baseline", [
    ("status", str),
    ("policy", Dict[str, Optional[float]]),     # None = PENDING
    ("controls", Dict[str, Optional[float]]),   # reference seconds
    ("probe_load", Optional[float]),  # expected in-run/idle probe median ratio
    ("global_phase", Dict[int, Optional[float]]),  # jobs -> normalized s
    ("classes", Dict[str, Optional[float]]),    # exact FQCN -> normalized s
])

Normalization = NamedTuple("Normalization", [
    ("valid", bool),
    ("factor", Optional[float]),
    ("factors", Dict[str, Optional[float]]),
    ("coherence", Optional[float]),
    ("transient", bool),
    ("reason", str),
])

ClassOutcome = NamedTuple("ClassOutcome", [
    ("name", str),
    ("parallel", float),
    ("confirmation", Optional[float]),
    ("classification", str),
    ("detail", str),
])


def _number(raw: str, where: str, positive: bool = True) -> Optional[float]:
    if raw == PENDING:
        return None
    try:
        value = float(raw)
    except ValueError:
        raise PolicyError("{}: unparseable number {!r}".format(where, raw))
    if math.isnan(value) or math.isinf(value) or (positive and value <= 0):
        raise PolicyError("{}: value must be a finite positive number: {!r}"
                          .format(where, raw))
    return value


def parse_baseline(path: Path) -> Baseline:
    """Parse the reviewed baseline file. Identities are exact; no globs."""
    if not path.is_file():
        raise PolicyError("baseline not found: {}".format(path))
    status = None  # type: Optional[str]
    policy = {}  # type: Dict[str, Optional[float]]
    controls = {}  # type: Dict[str, Optional[float]]
    global_phase = {}  # type: Dict[int, Optional[float]]
    classes = {}  # type: Dict[str, Optional[float]]
    probe = {}  # type: Dict[str, Optional[float]]
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split()
        where = "{}:{}".format(path, number)
        kind = fields[0]
        if kind == "status" and len(fields) == 2:
            if status is not None:
                raise PolicyError("{}: duplicate status".format(where))
            if fields[1] not in POLICY_STATUSES:
                raise PolicyError("{}: unknown status {!r}".format(where, fields[1]))
            status = fields[1]
        elif kind == "policy" and len(fields) == 3:
            if fields[1] not in POLICY_CONSTANTS:
                raise PolicyError("{}: unknown policy constant {!r}".format(where, fields[1]))
            _put(policy, fields[1], _number(fields[2], where), where)
        elif kind == "control" and len(fields) == 3:
            if fields[1] not in CONTROLS:
                raise PolicyError("{}: unknown control {!r}".format(where, fields[1]))
            _put(controls, fields[1], _number(fields[2], where), where)
        elif kind == "probe" and len(fields) == 3 and fields[1] == "load_ratio":
            _put(probe, "load_ratio", _number(fields[2], where), where)
        elif kind == "global" and len(fields) == 4 and fields[1] == "java_phase":
            try:
                jobs = int(fields[2])
            except ValueError:
                raise PolicyError("{}: unparseable jobs {!r}".format(where, fields[2]))
            if jobs <= 0:
                raise PolicyError("{}: jobs must be positive".format(where))
            _put(global_phase, jobs, _number(fields[3], where), where)
        elif kind == "class" and len(fields) == 3:
            name = fields[1]
            if any(ch in name for ch in "*?[]") or "." not in name:
                raise PolicyError("{}: class must be an exact fully-qualified name: {!r}"
                                  .format(where, name))
            _put(classes, name, _number(fields[2], where), where)
        else:
            raise PolicyError("{}: unrecognized entry {!r}".format(where, raw))
    if status is None:
        raise PolicyError("{}: missing status line".format(path))
    missing = [name for name in POLICY_CONSTANTS if name not in policy]
    missing += ["control " + name for name in CONTROLS if name not in controls]
    missing += ["probe load_ratio"] if not probe else []
    if missing:
        raise PolicyError("{}: missing entries: {}".format(path, ", ".join(missing)))
    if status == "APPROVED" and (None in policy.values() or None in controls.values()
                                 or probe["load_ratio"] is None):
        raise PolicyError("{}: APPROVED status with PENDING values".format(path))
    return Baseline(status, policy, controls, probe["load_ratio"], global_phase, classes)


def _put(table: Dict, key, value, where: str) -> None:
    if key in table:
        raise PolicyError("{}: duplicate entry {}".format(where, key))
    table[key] = value


def normalize(baseline: Baseline, pre: Dict[str, float],
              post: Dict[str, float], in_run_load: Optional[float] = None) -> Normalization:
    """Derive the common machine factor from the two control samples.

    in_run_load is the median in-run probe relative to the idle bracketing
    probe. The tests themselves load the machine, so it is compared with the
    reviewed normal-load ratio; beyond the coherence limit the run is
    transiently contended by something else. A Protos slowdown lengthens the
    run without raising its load, so it does not move this ratio."""
    policy = baseline.policy
    pending = [name for name in ("CONTROL_FACTOR_MIN", "CONTROL_FACTOR_MAX",
                                 "CONTROL_COHERENCE_LIMIT") if policy[name] is None]
    pending += [name for name in CONTROLS if baseline.controls[name] is None]
    pending += ["probe load_ratio"] if baseline.probe_load is None else []
    if pending:
        return Normalization(False, None, {}, None, False,
                             BASELINE_PENDING + ":" + ",".join(pending))
    low = policy["CONTROL_FACTOR_MIN"]
    high = policy["CONTROL_FACTOR_MAX"]
    limit = policy["CONTROL_COHERENCE_LIMIT"]
    factors = {}  # type: Dict[str, Optional[float]]
    transient = in_run_load is not None and in_run_load / baseline.probe_load > limit
    for name in CONTROLS:
        reference = baseline.controls[name]
        samples = [s[name] / reference for s in (pre, post)]
        factors[name] = min(samples)
        if max(samples) / min(samples) > limit:
            transient = True
    values = [factors[name] for name in CONTROLS]
    coherence = max(values) / min(values)
    outside = [name for name in CONTROLS if not low <= factors[name] <= high]
    if outside:
        return Normalization(False, None, factors, coherence, transient,
                             "factor outside [{:g},{:g}]: {}".format(low, high, ",".join(outside)))
    if coherence > limit:
        return Normalization(False, None, factors, coherence, transient,
                             "controls incoherent: {:.3f} > {:g}".format(coherence, limit))
    factor = math.exp(sum(math.log(v) for v in values) / len(values))
    return Normalization(True, factor, factors, coherence, transient, "coherent")


def suspects(timings: Dict[str, float], factor: Optional[float],
             baseline: Baseline) -> List[str]:
    """Classes needing one confirmation. Without a valid factor, raw time is
    used only to gather confirmation evidence for an ERROR report."""
    divisor = factor if factor is not None else 1.0
    class_factor = baseline.policy["CLASS_REGRESSION_FACTOR"]
    ceiling = baseline.policy["PATHOLOGICAL_CEILING_SECONDS"]
    result = []
    for name, elapsed in sorted(timings.items()):
        normalized = elapsed / divisor
        expected = baseline.classes.get(name)
        if ceiling is not None and elapsed > ceiling:
            result.append(name)
        elif expected is not None and class_factor is not None and factor is not None:
            # A reviewed class is suspicious only beyond its own envelope
            # (parallel time bounds isolated time, so below it nothing
            # regressed) and never below the ordinary threshold, so a cheap
            # reviewed class is not confirmed on every run.
            if normalized > max(SUSPICION_SECONDS, expected * class_factor):
                result.append(name)
        elif normalized > SUSPICION_SECONDS:
            result.append(name)
    return result


def classify_class(name: str, parallel: float, confirmation: Optional[float],
                   norm: Normalization, baseline: Baseline) -> ClassOutcome:
    """Classify one suspect from both preserved observations."""
    policy = baseline.policy
    ceiling = policy["PATHOLOGICAL_CEILING_SECONDS"]
    if confirmation is None:
        return ClassOutcome(name, parallel, None, CONFIGURATION_ERROR,
                            "no confirmation report")
    worst = max(parallel, confirmation)
    if ceiling is not None and worst > ceiling:
        return ClassOutcome(name, parallel, confirmation, PATHOLOGICAL_COST,
                            "{:.2f} s > ceiling {:g} s".format(worst, ceiling))
    if not norm.valid:
        return ClassOutcome(name, parallel, confirmation, ENVIRONMENT_NOT_COMPARABLE
                            if not norm.reason.startswith(BASELINE_PENDING)
                            else BASELINE_PENDING, norm.reason)
    pending = [key for key in ("CLASS_REGRESSION_FACTOR", "PARALLEL_INTERACTION_LIMIT",
                               "PATHOLOGICAL_CEILING_SECONDS") if policy[key] is None]
    if pending:
        return ClassOutcome(name, parallel, confirmation, BASELINE_PENDING,
                            ",".join(pending))
    normalized = confirmation / norm.factor
    expected = baseline.classes.get(name)
    if name in baseline.classes and expected is None:
        return ClassOutcome(name, parallel, confirmation, BASELINE_PENDING,
                            "class expectation PENDING; normalized {:.2f} s".format(normalized))
    if expected is None:
        if normalized > SUSPICION_SECONDS:
            return ClassOutcome(name, parallel, confirmation, NEW_UNBASELINED_EXPENSIVE_TEST,
                                "normalized {:.2f} s > {:g} s, no reviewed expectation; "
                                "review required".format(normalized, SUSPICION_SECONDS))
    elif normalized > expected * policy["CLASS_REGRESSION_FACTOR"]:
        return ClassOutcome(name, parallel, confirmation, ISOLATED_TEST_REGRESSION,
                            "normalized {:.2f} s > {:g} s x {:g}".format(
                                normalized, expected, policy["CLASS_REGRESSION_FACTOR"]))
    ratio = parallel / confirmation if confirmation > 0 else float("inf")
    if ratio > policy["PARALLEL_INTERACTION_LIMIT"]:
        detail = "parallel/confirmation {:.2f} > {:g}".format(
            ratio, policy["PARALLEL_INTERACTION_LIMIT"])
        if norm.transient:
            return ClassOutcome(name, parallel, confirmation, TRANSIENT_CONTENTION,
                                detail + "; control samples drifted")
        return ClassOutcome(name, parallel, confirmation,
                            PARALLEL_INTERACTION_REGRESSION, detail)
    return ClassOutcome(name, parallel, confirmation, CONFIRMED_NORMAL,
                        "normalized {:.2f} s, parallel/confirmation {:.2f}".format(normalized, ratio))


def classify_global(makespan: float, jobs: int, norm: Normalization,
                    baseline: Baseline) -> Tuple[str, Optional[float], str]:
    """Return (classification, normalized makespan, detail)."""
    if not norm.valid:
        return (BASELINE_PENDING if norm.reason.startswith(BASELINE_PENDING)
                else ENVIRONMENT_NOT_COMPARABLE), None, norm.reason
    if jobs not in baseline.global_phase:
        return CONFIGURATION_ERROR, None, "no global expectation for jobs={}".format(jobs)
    expected = baseline.global_phase[jobs]
    limit = baseline.policy["GLOBAL_REGRESSION_FACTOR"]
    normalized = makespan / norm.factor
    if expected is None or limit is None:
        return BASELINE_PENDING, normalized, "global expectation or factor PENDING"
    ratio = normalized / expected
    detail = "{:.2f} s / expected {:g} s = {:.3f} (limit {:g})".format(
        normalized, expected, ratio, limit)
    if ratio > limit:
        if norm.transient:
            # Load the controls saw change during the run inflates the
            # makespan without moving the factor: warn, do not convict.
            return TRANSIENT_CONTENTION, normalized, detail + "; machine load drifted"
        return GLOBAL_RUNTIME_REGRESSION, normalized, detail
    return NORMAL, normalized, detail


def verdict(global_class: str, outcomes: List[ClassOutcome]) -> Tuple[str, List[str]]:
    """Return (PASS|WARN|FAIL|ERROR, sorted distinct classifications)."""
    found = set([global_class] + [o.classification for o in outcomes])
    errors = found & set([ENVIRONMENT_NOT_COMPARABLE, BASELINE_PENDING, CONFIGURATION_ERROR])
    failing = found & FAILING
    if failing:
        # A proven product/cost failure stays visible even next to an error.
        status = "FAIL"
    elif errors:
        status = "ERROR"
    elif TRANSIENT_CONTENTION in found:
        status = "WARN"
    else:
        status = "PASS"
    shown = sorted(found - set([NORMAL, CONFIRMED_NORMAL])) or [NORMAL]
    return status, shown
