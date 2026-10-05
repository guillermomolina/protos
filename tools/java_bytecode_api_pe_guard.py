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


"""TEST009-C static Bytecode API PE-argument guard.

At oracle/graal@95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b these Bytecode DSL
API methods assert during partial evaluation that some arguments are PE
constants:

  BytecodeNode.getLocalValues(int bci, Frame)                 bci
  BytecodeNode.getLocalNames(int bci)                         bci
  BytecodeNode.getLocalInfos(int bci)                         bci
  BytecodeNode.setLocalValues(int bci, Frame, Object[])       bci
  BytecodeNode.copyLocalValues(int bci, Frame, Frame)         bci
  BytecodeNode.getLocalValue(int bci, Frame, int localOffset) bci, localOffset
  BytecodeNode.setLocalValue(int bci, Frame, int localOffset, Object)
                                                              bci, localOffset
  BytecodeNode.getLocalName(int bci, int localOffset)         bci, localOffset
  BytecodeNode.getLocalInfo(int bci, int localOffset)         bci, localOffset
  BytecodeNode.getLocalCount(int bci)                         bci
  BytecodeNode.copyLocalValues(int bci, Frame, Frame, int localOffset, int localCount)
                                                              bci, localOffset, localCount
  static BytecodeNode.get(Node)                               node
  BytecodeRootNodes.update(BytecodeConfig)                    config
  static BytecodeLocation.get(Node location, int bci)         location
  static BytecodeLocation.get(TruffleStackTraceElement)       delegates to get(Node, int)
                                                              with element.getLocation(),
                                                              so its node is never proven

Sinks are inventoried by name, arity, and the inferred static type of the
receiver (or the static-import owner of an unqualified call), never by a list
of Protos methods. A candidate whose receiver type cannot be inferred is
UNRESOLVED_RECEIVER_TYPE and fails closed whenever it is PE reachable.

Local argument provenance (per PE-required argument):

  INT_CONSTANT            integer literal
  STATIC_FINAL_FIELD      read of a source 'static final' field (folded by PE)
  API_CONSTANT            BytecodeConfig.DEFAULT / WITH_SOURCE / COMPLETE
  OPERAND_CONSTANT        positional int @ConstantOperand of a Bytecode DSL
                          operation specialization (bci, offset, count)
  BOUND_BYTECODE_INDEX    @Bind("$bytecodeIndex") int of an operation
                          specialization (bci)
  BOUND_NODE              @Bind("$node"/"$bytecodeNode"/"this") of a Truffle
                          DSL specialization or library export (node)
  HELPER_PARAMETER        ordinary parameter: followed through callers
  RUNTIME_VALUE           call result, instance field, other-object state,
                          loop variable: never proven
  STACK_TRACE_DERIVED     node derived by the BytecodeLocation.get wrapper
  UNKNOWN                 not reliably classified: never proven

A variable name ('bci', 'bytecodeIndex') proves nothing; neither does
node.getEnterBytecodeIndex() or location.getBytecodeIndex().

PE entries: Bytecode DSL operation specializations (the TEST009 roots), any
other Truffle DSL @Specialization/@Fallback, any @ExportMessage library
export, and any @Override instance method of a type with an external Truffle
'*Node' supertype (externally invoked node surfaces). @TruffleBoundary cuts PE
traversal; an unresolved edge that can connect an entry to a sink is
PE_REACHABILITY_UNKNOWN, as is a sink inside a lambda/anonymous body.

Per sink: PE_REACHABLE_PROVEN, PE_REACHABLE_RISK, BOUNDARY_CUT,
NOT_PE_REACHABLE, or PE_REACHABILITY_UNKNOWN. Risks and unknowns are never
baselineable; the checked-in topology baseline records every determinate
safe sink exactly, and a new, drifted, duplicate, or stale entry fails.

Subcommand:

  check --source DIR --baseline FILE [--report FILE]

Standard library only; one process; no compilation, Maven, Graal, or network.
"""

from __future__ import print_function

import argparse
import json
import sys
from collections import deque
from pathlib import Path
from typing import List, Optional

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_local_range_pe_guard as base  # noqa: E402
import java_local_range_operand_pe_guard as operand  # noqa: E402

BYTECODE_NODE = "BytecodeNode"
BYTECODE_ROOT_NODES = "BytecodeRootNodes"
BYTECODE_LOCATION = "BytecodeLocation"
API_TYPES = (BYTECODE_NODE, BYTECODE_ROOT_NODES, BYTECODE_LOCATION)
FAMILY_UNRESOLVED = "UNRESOLVED_RECEIVER_TYPE"

ROLE_BCI = "BYTECODE_INDEX"
ROLE_OFFSET = "LOCAL_OFFSET"
ROLE_COUNT = "LOCAL_COUNT"
ROLE_NODE = "NODE_ARGUMENT"
ROLE_CONFIG = "BYTECODE_CONFIG"
ROLES = (ROLE_BCI, ROLE_OFFSET, ROLE_COUNT, ROLE_NODE, ROLE_CONFIG)
WRAPPED_NODE = "WRAPPED_NODE"  # node extracted by a wrapper overload; reported as ROLE_NODE


class Spec(object):
    def __init__(self, family, name, arity, static, arguments):
        self.family = family
        self.name = name
        self.arity = arity
        self.static = static
        self.arguments = arguments  # ((argument position, role), ...)


SPECS = (
    Spec(BYTECODE_NODE, "getLocalValues", 2, False, ((0, ROLE_BCI),)),
    Spec(BYTECODE_NODE, "getLocalNames", 1, False, ((0, ROLE_BCI),)),
    Spec(BYTECODE_NODE, "getLocalInfos", 1, False, ((0, ROLE_BCI),)),
    Spec(BYTECODE_NODE, "setLocalValues", 3, False, ((0, ROLE_BCI),)),
    Spec(BYTECODE_NODE, "copyLocalValues", 3, False, ((0, ROLE_BCI),)),
    Spec(BYTECODE_NODE, "copyLocalValues", 5, False, ((0, ROLE_BCI), (3, ROLE_OFFSET), (4, ROLE_COUNT))),
    Spec(BYTECODE_NODE, "getLocalValue", 3, False, ((0, ROLE_BCI), (2, ROLE_OFFSET))),
    Spec(BYTECODE_NODE, "setLocalValue", 4, False, ((0, ROLE_BCI), (2, ROLE_OFFSET))),
    Spec(BYTECODE_NODE, "getLocalName", 2, False, ((0, ROLE_BCI), (1, ROLE_OFFSET))),
    Spec(BYTECODE_NODE, "getLocalInfo", 2, False, ((0, ROLE_BCI), (1, ROLE_OFFSET))),
    Spec(BYTECODE_NODE, "getLocalCount", 1, False, ((0, ROLE_BCI),)),
    Spec(BYTECODE_NODE, "get", 1, True, ((0, ROLE_NODE),)),
    Spec(BYTECODE_ROOT_NODES, "update", 1, False, ((0, ROLE_CONFIG),)),
    Spec(BYTECODE_LOCATION, "get", 2, True, ((0, ROLE_NODE),)),
    Spec(BYTECODE_LOCATION, "get", 1, True, ((0, WRAPPED_NODE),)),
)
SPECS_BY_NAME = {}
for _spec in SPECS:
    SPECS_BY_NAME.setdefault(_spec.name, []).append(_spec)
LOCAL_TABLE_OPERATIONS = ("getLocalValues", "getLocalNames", "getLocalInfos", "setLocalValues", "copyLocalValues")
LOCAL_SLOT_OPERATIONS = ("getLocalValue", "setLocalValue", "getLocalName", "getLocalInfo", "getLocalCount")

# External Graal API members whose result type is one of the API types; used
# only when the source hierarchy cannot type the receiver (it can only add sinks).
API_RETURNS = {"getRootNodes": BYTECODE_ROOT_NODES, "getBytecodeNode": BYTECODE_NODE,
               "getBytecodeLocation": BYTECODE_LOCATION}
BYTECODE_CONFIG_CONSTANTS = ("DEFAULT", "WITH_SOURCE", "COMPLETE")

INT_CONSTANT = "INT_CONSTANT"
STATIC_FINAL_FIELD = "STATIC_FINAL_FIELD"
API_CONSTANT = "API_CONSTANT"
OPERAND_CONSTANT = "OPERAND_CONSTANT"
BOUND_BYTECODE_INDEX = "BOUND_BYTECODE_INDEX"
BOUND_NODE = "BOUND_NODE"
HELPER_PARAMETER = "HELPER_PARAMETER"
RUNTIME_VALUE = "RUNTIME_VALUE"
STACK_TRACE_DERIVED = "STACK_TRACE_DERIVED"
UNKNOWN = "UNKNOWN"
ROOT_RUNTIME_OPERAND = "ROOT_RUNTIME_OPERAND"
UNPROVEN_ARGUMENT = "UNPROVEN_ARGUMENT"
LOCAL_CLASSES = (INT_CONSTANT, STATIC_FINAL_FIELD, API_CONSTANT, OPERAND_CONSTANT, BOUND_BYTECODE_INDEX, BOUND_NODE,
                 HELPER_PARAMETER, RUNTIME_VALUE, STACK_TRACE_DERIVED, UNKNOWN)
SAFE_CLASSES = (INT_CONSTANT, STATIC_FINAL_FIELD, API_CONSTANT, OPERAND_CONSTANT, BOUND_BYTECODE_INDEX, BOUND_NODE)

PE_REACHABLE_PROVEN = "PE_REACHABLE_PROVEN"
PE_REACHABLE_RISK = "PE_REACHABLE_RISK"
BOUNDARY_CUT = base.BOUNDARY_CUT
NOT_PE_REACHABLE = base.NOT_PE_REACHABLE
PE_REACHABILITY_UNKNOWN = base.PE_REACHABILITY_UNKNOWN
PE_CLASSES = (PE_REACHABLE_PROVEN, PE_REACHABLE_RISK, BOUNDARY_CUT, NOT_PE_REACHABLE, PE_REACHABILITY_UNKNOWN)
PROVEN = "PROVEN"
RISK = "RISK"

BASELINE_SCHEMA = "protos-bytecode-api-pe-baseline-v1"
REPORT_SCHEMA = "protos-bytecode-api-pe-report-v1"
CANDIDATE_NAME = "bytecode-api-pe-baseline.candidate.json"
IDENTITY_KEYS = ("path", "class", "method", "family", "operation", "arity", "arguments", "occurrence")
BASELINE_KEYS = IDENTITY_KEYS + ("provenance", "pe_reachability")

GuardError = base.GuardError


def _report_role(role):
    return ROLE_NODE if role == WRAPPED_NODE else role


class Argument(object):
    def __init__(self, position, role):
        self.position = position
        self.role = role
        self.text = "<unavailable>"
        self.klass = UNKNOWN
        self.reason = ""
        self.param = None
        self.pe = None


class ApiSink(object):
    def __init__(self, site, family, spec, arity):
        self.site = site
        self.family = family
        self.spec = spec
        self.path = site.java.label
        self.line = site.line
        self.type_name = site.owner.qualified if site.owner is not None else "<unknown>"
        self.method = site.method.signature if site.method is not None else "<outside-method>"
        self.operation = site.name
        self.arity = arity
        self.arguments = [Argument(position, role) for position, role in spec.arguments]
        self.occurrence = 0
        self.boundary = False
        self.opaque = bool(site.opaque) or site.args is None
        self.pe_reachability = None
        self.pe_paths = []
        self.pe_call_chain = []
        self.pe_unknown = []
        self.status = None

    def arguments_text(self):
        return ", ".join("%s=%s" % (_report_role(each.role), each.text) for each in self.arguments)

    def provenance(self):
        return ";".join("%s=%s" % (_report_role(each.role), each.klass) for each in self.arguments)

    def identity(self):
        return (self.path, self.type_name, self.method, self.family, self.operation, self.arity,
                self.arguments_text(), self.occurrence)

    def entry(self):
        entry = dict(zip(IDENTITY_KEYS, self.identity()))
        entry["provenance"] = self.provenance()
        entry["pe_reachability"] = self.pe_reachability
        return entry

    def label(self):
        return "%s:%d %s.%s %s.%s/%s(%s)" % (
            self.path, self.line, self.type_name, self.method, self.family, self.operation, self.arity,
            self.arguments_text())

    def to_json(self):
        document = self.entry()
        document.update({
            "line": self.line,
            "argument_reasons": dict((_report_role(each.role), each.reason) for each in self.arguments),
            "argument_pe": dict((_report_role(each.role), each.pe) for each in self.arguments),
            "enclosing_truffle_boundary": self.boundary,
            "pe_paths": [dict(path) for path in self.pe_paths],
            "pe_call_chain": list(self.pe_call_chain),
            "pe_unknown": list(self.pe_unknown),
            "status": self.status,
        })
        return document


# --------------------------------------------------------------------------
# PE entries and root proofs
# --------------------------------------------------------------------------

DSL_ENTRY_ANNOTATIONS = frozenset(["Specialization", "Fallback", "ExportMessage"])


def _truffle_node_type(graph, info):
    for each in graph.hierarchy_types(info):
        external = each.super_ids - set(name for name in each.supers if name in graph.types_by_name)
        if any(name.endswith("Node") for name in external):
            return True
    return False


def entry_kind(graph, method):
    """Why 'method' may start a partial evaluation, or None."""
    if method.abstract or method.body_open < 0 or method.owner is None:
        return None
    if base.is_pe_root(method):
        return "BYTECODE_OPERATION"
    names = base._annotation_names(method.annotations)
    if names & DSL_ENTRY_ANNOTATIONS:
        return "TRUFFLE_DSL"
    if "Override" in names and "static" not in method.modifiers and _truffle_node_type(graph, method.owner):
        return "NODE_OVERRIDE"
    return None


def _bind_expression(java, param):
    for name, start, end in param.annotations:
        if name != "Bind":
            continue
        if not java.is_op(end, ")"):
            # Bare @Bind: the DSL infers "$bytecodeNode" from the BytecodeNode parameter type.
            if param.type_name == "BytecodeNode":
                return '"$bytecodeNode"'
            continue
        opener = java.matches[end]
        if end - opener == 2:
            return java.text(opener + 1)
    return None


def _is_dsl_method(method):
    return bool(base._annotation_names(method.annotations) & DSL_ENTRY_ANNOTATIONS)


def prove_int(role):
    def prove(java, method, param) -> Optional[str]:
        proof = base._prove_constant_operand(java, method, param)
        if proof is not None:
            return OPERAND_CONSTANT
        if (role == ROLE_BCI and base.is_pe_root(method) and param.type_name == "int"
                and _bind_expression(java, param) == '"$bytecodeIndex"'):
            return BOUND_BYTECODE_INDEX
        return None
    return prove


def prove_node(java, method, param) -> Optional[str]:
    if _is_dsl_method(method) and _bind_expression(java, param) in ('"$node"', '"$bytecodeNode"', '"this"'):
        return BOUND_NODE
    return None


def prove_nothing(java, method, param) -> Optional[str]:
    return None


PROVERS = {ROLE_BCI: prove_int(ROLE_BCI), ROLE_OFFSET: prove_int(ROLE_OFFSET), ROLE_COUNT: prove_int(ROLE_COUNT),
           ROLE_NODE: prove_node, ROLE_CONFIG: prove_nothing, WRAPPED_NODE: prove_nothing}


# --------------------------------------------------------------------------
# Local argument provenance
# --------------------------------------------------------------------------


def _static_final(declaration):
    return declaration.kind == "field" and "static" in declaration.modifiers and "final" in declaration.modifiers


def classifier(graph, role):
    prove = PROVERS[role]

    def classify(java, method, start, end, at, found, seen=frozenset()):
        if role == WRAPPED_NODE:
            return STACK_TRACE_DERIVED, ("the wrapper overload derives its location Node from the runtime element "
                                         "(element.getLocation()); never proven")
        count = end - start
        if count == 1 and role in (ROLE_BCI, ROLE_OFFSET, ROLE_COUNT) and base._is_int_literal(java.tokens[start]):
            return INT_CONSTANT, "integer literal %s" % java.text(start)
        if count == 3 and java.is_id(start) and java.is_op(start + 1, ".") and java.is_id(start + 2):
            owner, member = java.text(start), java.text(start + 2)
            if owner == "this":
                declaration = java.lookup(method, member, at, fields_only=True)
                if declaration is not None and _static_final(declaration):
                    return STATIC_FINAL_FIELD, "static final field 'this.%s'" % member
                return RUNTIME_VALUE, "read from field 'this.%s'" % member
            if role == ROLE_CONFIG and owner == "BytecodeConfig" and member in BYTECODE_CONFIG_CONSTANTS:
                return API_CONSTANT, "static final API constant BytecodeConfig.%s" % member
            if owner[:1].isupper() and java.lookup(method, owner, at) is None:
                for info in graph.types_by_name.get(owner, ()):
                    declaration = info.fields.get(member)
                    if declaration is not None and _static_final(declaration):
                        return STATIC_FINAL_FIELD, "static final field %s.%s" % (owner, member)
            return RUNTIME_VALUE, "read from another object or type: %s" % base.normalize(java, start, end)
        if operand._qualified_chain(java, start, end):
            return RUNTIME_VALUE, "read from another object: %s" % base.normalize(java, start, end)
        if java.is_op(end - 1, ")"):
            return RUNTIME_VALUE, "call result %s is never a proof" % base.normalize(java, start, end)
        if not operand._single_id(java, start, end):
            return UNKNOWN, "compound expression %s" % base.normalize(java, start, end)
        name = java.text(start)
        if name in seen or len(seen) > 8:
            return UNKNOWN, "cyclic or too deep local alias chain at '%s'" % name
        declaration = java.lookup(method, name, at)
        if declaration is None:
            return UNKNOWN, "'%s' does not resolve to a local, parameter, or field" % name
        if declaration.kind == "param":
            written = base._reassigned(java, name, method.body_open, method.body_close)
            if written is not None:
                return UNKNOWN, "parameter '%s' is reassigned at line %d" % (name, java.tokens[written].line)
            # A proven root parameter is still tracked: the root may also be called directly as a helper.
            found.append(declaration)
            proof = prove(java, method, declaration)
            if proof is not None:
                return proof, "parameter '%s' is proven %s at its own PE root" % (name, proof)
            return HELPER_PARAMETER, "ordinary %s parameter '%s'" % (declaration.type_name, name)
        if declaration.kind == "field":
            if _static_final(declaration):
                return STATIC_FINAL_FIELD, "static final field '%s'" % name
            modifiers = " ".join(sorted(declaration.modifiers)) or "non-final"
            return RUNTIME_VALUE, "read from %s field '%s'; only a static final field is folded" % (modifiers, name)
        if declaration.kind != "local":
            return RUNTIME_VALUE, "loop variable '%s'" % name
        return operand._local_alias(java, method, declaration, found, seen | {name}, classify, UNKNOWN)

    return classify


# --------------------------------------------------------------------------
# Inventory by receiver type
# --------------------------------------------------------------------------


def _api_return(java, start, end):
    """API type of a receiver expression ending in a zero-argument API member call, or None."""
    if end - start >= 3 and java.is_op(end - 1, ")") and java.matches[end - 1] == end - 2 and java.is_id(end - 3):
        return API_RETURNS.get(java.text(end - 3))
    return None


def _family_of_type(graph, receiver, family):
    """True / False / None (unresolved): may 'receiver' be of the API type 'family'?"""
    if receiver.kind == base.T_KNOWN:
        if receiver.name == family:
            return True
        return family in graph.super_names_of(receiver.name)
    if receiver.kind == base.T_SUPER:
        return None if family in receiver.ids else False
    return None


def _resolve_family(graph, site, specs):
    """(family or FAMILY_UNRESOLVED, matching specs) or None when the call is certainly not a sink."""
    families = sorted(set(spec.family for spec in specs))
    if site.dot is None:
        owners = set()
        if graph.lexical_type(site.owner, site.name) is None:
            for type_name, member in site.java.static_imports:
                if type_name in families and member in (site.name, "*"):
                    owners.add(type_name)
        if len(owners) != 1:
            return None
        family = owners.pop()
        return family, [spec for spec in specs if spec.family == family and spec.static]
    receiver = graph.receiver_type(site)
    end = site.dot
    start = base._chain_start(site.java, end - 1)
    if receiver is None or receiver.kind == base.T_EXTERNAL:
        returned = _api_return(site.java, start, end)
        if returned is not None:
            receiver = base._known(returned)
    if receiver is not None and receiver.kind == base.T_STATIC:
        if receiver.name in families:
            return receiver.name, [spec for spec in specs if spec.family == receiver.name and spec.static]
        return None
    if receiver is not None and receiver.kind in (base.T_KNOWN, base.T_SUPER):
        resolved = []
        unresolved = False
        for family in families:
            answer = _family_of_type(graph, receiver, family)
            if answer is True:
                resolved.append(family)
            elif answer is None:
                unresolved = True
        if len(resolved) == 1 and not unresolved:
            return resolved[0], [spec for spec in specs if spec.family == resolved[0]]
        if not resolved and not unresolved:
            return None
        return FAMILY_UNRESOLVED, specs
    # Untyped or externally typed receiver: an instance member may be a sink. A
    # static-only member through an instance expression is a sink candidate only
    # when the expression's type identifiers name its API type.
    instance = [spec for spec in specs if not spec.static]
    static = [spec for spec in specs if spec.static and receiver is not None and spec.family in receiver.ids]
    if instance or static:
        return FAMILY_UNRESOLVED, instance + static
    return None


def classify_sink(graph, sink: ApiSink):
    site = sink.site
    java = site.java
    method = site.method
    if method is None:
        for argument in sink.arguments:
            argument.reason = "call outside a method body"
        return
    sink.boundary = base.is_truffle_boundary(method)
    for argument in sink.arguments:
        if site.args is not None and argument.position < len(site.args) and site.args[argument.position]:
            tokens = site.args[argument.position]
            argument.text = base.normalize(java, tokens[0], tokens[-1] + 1)
        if sink.family == FAMILY_UNRESOLVED:
            argument.reason = "receiver type is not statically resolvable; it may be %s" % " or ".join(
                sorted(set(spec.family for spec in SPECS_BY_NAME[sink.operation])))
            continue
        if site.args is None:
            argument.reason = "method reference to a Bytecode API operation"
            continue
        if site.opaque:
            argument.reason = "call inside a lambda or anonymous class body"
            continue
        if argument.text == "<unavailable>":
            argument.reason = "missing argument"
            continue
        tokens = site.args[argument.position]
        found = []
        argument.klass, argument.reason = classifier(graph, argument.role)(
            java, method, tokens[0], tokens[-1] + 1, site.index, found)
        argument.param = found[-1] if found and found[-1] in method.params else None


def inventory(graph) -> List[ApiSink]:
    sinks = []
    for name in sorted(SPECS_BY_NAME):
        for site in graph.sites_by_name.get(name, ()):
            if site.kind not in ("call", "ref"):
                continue
            arity = len(site.args) if site.args is not None else None
            specs = [spec for spec in SPECS_BY_NAME[name] if arity is None or spec.arity == arity]
            if not specs:
                continue
            resolved = _resolve_family(graph, site, specs)
            if resolved is None or not resolved[1]:
                continue
            family, matching = resolved
            if family != FAMILY_UNRESOLVED and len(matching) != 1:
                family = FAMILY_UNRESOLVED
            spec = matching[0]
            if family == FAMILY_UNRESOLVED:
                # Every candidate overload's PE-required positions, unproven.
                positions = {}
                for each in matching:
                    for position, role in each.arguments:
                        positions.setdefault(position, role)
                spec = Spec(FAMILY_UNRESOLVED, name, arity, False, tuple(sorted(positions.items())))
            sinks.append(ApiSink(site, family, spec, arity if arity is not None else "ref"))
    sinks.sort(key=lambda sink: (sink.path, sink.line, sink.site.index))
    for sink in sinks:
        classify_sink(graph, sink)
    occurrences = {}
    for sink in sinks:
        identity = sink.identity()[:-1]
        sink.occurrence = occurrences.get(identity, 0)
        occurrences[identity] = sink.occurrence + 1
    return sinks


# --------------------------------------------------------------------------
# PE reachability, every PE-required argument jointly per path
# --------------------------------------------------------------------------


def _argument_dimension(graph, site, target, dimension, role):
    if dimension[0] == "prov":
        return dimension
    tracked = dimension[1]
    caller = site.caller
    if site.args is None or caller.body_open < 0 or site.method is None or site.opaque:
        return ("prov", UNPROVEN_ARGUMENT)
    if target.varargs and tracked >= len(target.params) - 1:
        return ("prov", UNPROVEN_ARGUMENT)
    if tracked >= len(site.args) or not site.args[tracked]:
        return ("prov", UNPROVEN_ARGUMENT)
    argument = site.args[tracked]
    found = []
    classification, _ = classifier(graph, role)(site.java, caller, argument[0], argument[-1] + 1, site.index, found)
    if found and found[-1] in caller.params and classification not in (UNKNOWN, RUNTIME_VALUE):
        return ("param", caller.params.index(found[-1]))
    return ("prov", classification)


def _root_dimension(root, dimension, role):
    if dimension[0] == "prov":
        return dimension[1]
    proof = PROVERS[role](root.java, root, root.params[dimension[1]])
    return proof if proof is not None else ROOT_RUNTIME_OPERAND


def classify_reachability(graph, sink: ApiSink):
    method = sink.site.method
    sink.pe_paths = []
    sink.pe_call_chain = []
    sink.pe_unknown = []
    if method is None:
        sink.pe_reachability = PE_REACHABILITY_UNKNOWN
        sink.pe_unknown = ["call outside a method body"]
        return
    if sink.boundary:
        sink.pe_reachability = BOUNDARY_CUT
        sink.pe_call_chain = [base.method_key(method)]
        return
    if sink.opaque:
        sink.pe_reachability = PE_REACHABILITY_UNKNOWN
        sink.pe_unknown = ["%s: the calling context of a lambda/anonymous body or method reference is not "
                           "determinable" % sink.site.location()]
        return
    roles = tuple(argument.role for argument in sink.arguments)
    dimensions = tuple(
        ("param", method.params.index(argument.param)) if argument.param is not None else ("prov", argument.klass)
        for argument in sink.arguments)
    first = ((method, dimensions), False)
    parent = {first: None}
    queue = deque([first])
    terminals = {}
    roots = {}
    cuts = []
    unknown = []

    def add_unknown(reason):
        graph.unknown_edges.add(reason.split("; reaches PE entry", 1)[0])
        if reason not in unknown:
            unknown.append(reason)

    while queue:
        current_node = queue.popleft()
        (current, state), uncertain = current_node
        kind = entry_kind(graph, current)
        if kind is not None:
            key = tuple(_root_dimension(current, dimension, role) for dimension, role in zip(state, roles))
            if uncertain:
                add_unknown("%s; reaches PE entry %s" % (graph._uncertain_reason(parent, current_node),
                                                         base.method_key(current)))
            else:
                if key not in terminals:
                    terminals[key] = graph._chain(parent, current_node)
                roots.setdefault(key, set()).add("%s [%s]" % (base.method_key(current), kind))
            # An entry may also be invoked directly as a helper: keep following its callers.
        for site, status, reason in graph.callers(current):
            if status == base.EDGE_IMMEDIATE:
                add_unknown(reason)
                continue
            caller = site.caller
            edge_uncertain = uncertain or status == base.EDGE_UNCERTAIN
            if base.is_truffle_boundary(caller):
                if not edge_uncertain:
                    chain = [base.method_key(caller)] + graph._chain(parent, current_node)
                    if len(cuts) < 3 and chain not in cuts:
                        cuts.append(chain)
                continue
            following_state = tuple(_argument_dimension(graph, site, current, dimension, role)
                                    for dimension, role in zip(state, roles))
            following = ((caller, following_state), edge_uncertain)
            if following in parent or (edge_uncertain and ((caller, following_state), False) in parent):
                continue
            parent[following] = (current_node, reason if status == base.EDGE_UNCERTAIN else None, site)
            if len(parent) > base.SEARCH_STATE_LIMIT:
                add_unknown("reachability search exceeded %d states" % base.SEARCH_STATE_LIMIT)
                queue.clear()
                break
            queue.append(following)

    def safe(key):
        return all(value in SAFE_CLASSES for value in key)

    ordered = sorted(terminals, key=lambda key: (safe(key), key))
    sink.pe_paths = [
        {"arguments": dict((_report_role(role), value) for role, value in zip(roles, key)),
         "entries": sorted(roots[key]), "chain": terminals[key]}
        for key in ordered
    ]
    sink.pe_unknown = unknown
    if unknown:
        sink.pe_reachability = PE_REACHABILITY_UNKNOWN
    elif terminals and sink.family == FAMILY_UNRESOLVED:
        sink.pe_reachability = PE_REACHABILITY_UNKNOWN
        sink.pe_unknown = ["receiver type is unresolved and the call is PE reachable via %s" % " -> ".join(
            base.short_key(each) for each in terminals[ordered[0]])]
    elif terminals:
        for position, argument in enumerate(sink.arguments):
            argument.pe = PROVEN if all(key[position] in SAFE_CLASSES for key in terminals) else RISK
        risky = [key for key in terminals if not safe(key)]
        sink.pe_reachability = PE_REACHABLE_RISK if risky else PE_REACHABLE_PROVEN
        sink.pe_call_chain = min((terminals[key] for key in (risky or terminals)), key=len)
    elif cuts:
        sink.pe_reachability = BOUNDARY_CUT
        sink.pe_call_chain = cuts[0]
    else:
        sink.pe_reachability = NOT_PE_REACHABLE


# --------------------------------------------------------------------------
# Scanning
# --------------------------------------------------------------------------

RELEVANT_IDS = (frozenset(API_TYPES) | frozenset(["BytecodeConfig"]) | frozenset(LOCAL_TABLE_OPERATIONS)
                | frozenset(LOCAL_SLOT_OPERATIONS))


def parse_sources(items):
    """Lexes and parses (label, source) pairs; a relevant unparsable file is fatal."""
    parsed = []
    for label, source in items:
        tokens = base.lex(source, label)
        relevant = any(token.kind == "id" and token.text in RELEVANT_IDS for token in tokens)
        try:
            java = base.JavaFile(label, tokens)
            error = None
        except (GuardError, IndexError, KeyError, ValueError) as failure:
            if relevant:
                raise GuardError("%s: cannot parse a file that mentions a Bytecode API type or operation: %s" % (
                    label, failure))
            java = None
            error = str(failure)
        parsed.append(base.ParsedFile(label, tokens, java, error))
    return parsed


def analyze(parsed_files):
    graph = base.CallGraph(parsed_files)
    sinks = inventory(graph)
    for sink in sinks:
        classify_reachability(graph, sink)
    return sinks, graph


def analyze_sources(items):
    """Analyzes (label, source) pairs; returns (sinks, call graph)."""
    return analyze(parse_sources(items))


def scan_tree(source_root: Path, label_root: Path):
    if not source_root.is_dir():
        raise GuardError("source directory does not exist: %s" % source_root)
    items = []
    for path in sorted(source_root.rglob("*.java")):
        if "target" in path.relative_to(source_root).parts:
            continue
        try:
            label = path.resolve().relative_to(label_root.resolve()).as_posix()
        except ValueError:
            label = path.as_posix()
        try:
            source = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as error:
            raise GuardError("%s: cannot read: %s" % (label, error))
        items.append((label, source))
    return parse_sources(items)


# --------------------------------------------------------------------------
# Verification
# --------------------------------------------------------------------------

STATUS_SAFE = "BASELINED_SAFE"
STATUS_RISK = "PE_REACHABLE_RISK"
STATUS_UNKNOWN = "UNKNOWN"
STATUS_NEW = "NEW_UNBASELINED_SITE"
STATUS_DRIFT = "TOPOLOGY_DRIFT"


def _parse_provenance(text):
    parts = {}
    for part in text.split(";"):
        role, separator, klass = part.partition("=")
        if not separator or role not in ROLES or klass not in LOCAL_CLASSES or role in parts:
            return None
        parts[role] = klass
    return parts


def load_baseline(path: Path) -> List[dict]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise GuardError("cannot read baseline %s: %s" % (path, error))
    if not isinstance(data, dict) or data.get("schema") != BASELINE_SCHEMA:
        raise GuardError("baseline %s: schema must be %s" % (path, BASELINE_SCHEMA))
    entries = data.get("entries")
    if not isinstance(entries, list):
        raise GuardError("baseline %s: 'entries' must be a list" % path)
    for position, entry in enumerate(entries):
        if not isinstance(entry, dict) or set(entry) != set(BASELINE_KEYS):
            raise GuardError("baseline entry %d must be an object with exactly the keys %s" % (
                position, sorted(BASELINE_KEYS)))
        for key in BASELINE_KEYS:
            value = entry[key]
            if key in ("occurrence", "arity"):
                if not isinstance(value, int) or isinstance(value, bool) or value < 0:
                    raise GuardError("baseline entry %d: %s must be a non-negative integer" % (position, key))
            elif not isinstance(value, str) or not value or "*" in value:
                raise GuardError("baseline entry %d: %s must be a non-empty exact (non-wildcard) string" % (
                    position, key))
        if entry["family"] not in API_TYPES + (FAMILY_UNRESOLVED,):
            raise GuardError("baseline entry %d: family %r is unknown" % (position, entry["family"]))
        provenance = _parse_provenance(entry["provenance"])
        if provenance is None:
            raise GuardError("baseline entry %d: malformed provenance %r" % (position, entry["provenance"]))
        if entry["pe_reachability"] not in (PE_REACHABLE_PROVEN, BOUNDARY_CUT, NOT_PE_REACHABLE):
            raise GuardError("baseline entry %d: pe_reachability %r is not baselineable" % (
                position, entry["pe_reachability"]))
        if entry["pe_reachability"] == PE_REACHABLE_PROVEN and (
                entry["family"] == FAMILY_UNRESOLVED
                or any(klass not in SAFE_CLASSES + (HELPER_PARAMETER,) for klass in provenance.values())):
            raise GuardError("baseline entry %d: a PE-reachable non-proven provenance is a risk and is never "
                             "baselineable" % position)
    return entries


def verify(sinks: List[ApiSink], entries: List[dict]) -> List[str]:
    failures = []
    for sink in sinks:
        if sink.pe_reachability == PE_REACHABILITY_UNKNOWN:
            sink.status = STATUS_UNKNOWN
            failures.append("PE_REACHABILITY_UNKNOWN %s: %s" % (sink.label(), " | ".join(
                sink.pe_unknown[:5]) + (" | ..." if len(sink.pe_unknown) > 5 else "")))
        elif sink.pe_reachability == PE_REACHABLE_RISK:
            sink.status = STATUS_RISK
            chain = " -> ".join(base.short_key(each) for each in sink.pe_call_chain)
            for argument in sink.arguments:
                if argument.pe == RISK:
                    failures.append("PE_REACHABLE_%s_RISK %s (%s: %s) via %s" % (
                        _report_role(argument.role), sink.label(), argument.klass, argument.reason, chain))
    seen = {}
    for entry in entries:
        key = tuple(entry[key] for key in IDENTITY_KEYS)
        if key in seen:
            failures.append("DUPLICATE_BASELINE_ENTRY %s" % "|".join(str(part) for part in key))
        seen[key] = entry
    matched = set()
    for sink in sinks:
        if sink.status is not None:
            continue
        entry = seen.get(sink.identity())
        if entry is None:
            sink.status = STATUS_NEW
            failures.append("NEW_UNBASELINED_SITE %s %s" % (sink.label(), json.dumps(sink.entry())))
            continue
        matched.add(sink.identity())
        actual = sink.entry()
        drift = [key for key in BASELINE_KEYS if entry[key] != actual[key]]
        if drift:
            sink.status = STATUS_DRIFT
            failures.append("TOPOLOGY_DRIFT %s: %s" % (sink.label(), "; ".join(
                "%s expected %s actual %s" % (key, entry[key], actual[key]) for key in drift)))
        else:
            sink.status = STATUS_SAFE
    for key in seen:
        if key not in matched and not any(sink.identity() == key for sink in sinks):
            failures.append("STALE_BASELINE_ENTRY %s" % "|".join(str(part) for part in key))
    return failures


def totals_of(sinks, graph):
    def family(name, operations=None):
        return sum(1 for sink in sinks if sink.family == name and (operations is None or sink.operation in operations))

    totals = {
        "TOTAL_BYTECODE_API_SINKS": len(sinks),
        "BYTECODE_NODE_LOCAL_TABLE_SINKS": family(BYTECODE_NODE, LOCAL_TABLE_OPERATIONS),
        "BYTECODE_NODE_LOCAL_SLOT_SINKS": family(BYTECODE_NODE, LOCAL_SLOT_OPERATIONS),
        "BYTECODE_NODE_GET_SINKS": family(BYTECODE_NODE, ("get",)),
        "BYTECODE_ROOT_NODES_UPDATE_SINKS": family(BYTECODE_ROOT_NODES),
        "BYTECODE_LOCATION_GET_SINKS": family(BYTECODE_LOCATION),
        "UNRESOLVED_RECEIVER_TYPE_SINKS": family(FAMILY_UNRESOLVED),
    }
    for name in LOCAL_SLOT_OPERATIONS:
        snake = "".join("_" + char if char.isupper() else char.upper() for char in name)
        totals["%s_SINKS" % snake] = family(BYTECODE_NODE, (name,))
    reachable = [sink for sink in sinks if sink.pe_reachability in (PE_REACHABLE_PROVEN, PE_REACHABLE_RISK)]
    for role in ROLES:
        for value in (PROVEN, RISK):
            totals["PE_REACHABLE_%s_%s" % (role, value)] = sum(
                1 for sink in reachable for argument in sink.arguments
                if _report_role(argument.role) == role and argument.pe == value)
    for classification in PE_CLASSES:
        totals[classification] = sum(1 for sink in sinks if sink.pe_reachability == classification)
    totals["PE_ROOTS"] = len(graph.roots)
    totals["CALL_GRAPH_AMBIGUITIES"] = len(graph.unknown_edges)
    totals["UNPARSED_FILES"] = len(graph.unparsed)
    for status in (STATUS_NEW, STATUS_DRIFT):
        totals[status] = sum(1 for sink in sinks if sink.status == status)
    return totals


ACCEPTANCE_KEYS = tuple("PE_REACHABLE_%s_RISK" % role for role in ROLES) + ("PE_REACHABILITY_UNKNOWN",)


def check(source: Path, baseline: Path, report: Optional[Path], label_root: Path, out=sys.stdout) -> int:
    try:
        parsed = scan_tree(source, label_root)
        entries = load_baseline(baseline)
    except GuardError as error:
        print("bytecode-api-pe-guard: ANALYSIS_FAILED: %s" % error, file=out)
        return 1
    sinks, graph = analyze(parsed)
    failures = verify(sinks, entries)
    totals = totals_of(sinks, graph)
    totals["STALE_BASELINE_ENTRIES"] = sum(1 for failure in failures if failure.startswith("STALE_"))
    candidate = [sink.entry() for sink in sinks if sink.status in (STATUS_SAFE, STATUS_NEW, STATUS_DRIFT)]
    candidate_path = None
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        document = {
            "schema": REPORT_SCHEMA,
            "result": "PASS" if not failures else "FAIL",
            "totals": totals,
            "failures": failures,
            "call_graph_unknown_edges": sorted(graph.unknown_edges),
            "sinks": [sink.to_json() for sink in sinks],
        }
        report.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
        candidate_path = report.parent / CANDIDATE_NAME
        candidate_path.write_text(json.dumps({
            "schema": BASELINE_SCHEMA,
            "description": "Exact topology of every determinate PE-safe Bytecode API PE-argument sink. Risks are "
                           "never baselined.",
            "entries": candidate,
        }, indent=2) + "\n", encoding="utf-8")
    print("bytecode-api-pe-guard: BytecodeNode/BytecodeRootNodes/BytecodeLocation PE-argument provenance", file=out)
    print("  %-22s %-24s %-48s %s" % ("STATUS", "PE", "PROVENANCE", "SINK"), file=out)
    for sink in sinks:
        print("  %-22s %-24s %-48s %s:%d %s.%s %s.%s/%s(%s)" % (
            sink.status, sink.pe_reachability, sink.provenance(), sink.path.rsplit("/", 1)[-1], sink.line,
            sink.type_name.rsplit(".", 1)[-1], sink.method.split("(", 1)[0], sink.family, sink.operation, sink.arity,
            sink.arguments_text()), file=out)
    risks = [sink for sink in sinks if sink.status in (STATUS_RISK, STATUS_UNKNOWN)]
    if risks:
        print("bytecode-api-pe-guard: PE-reachable risks and unknowns (every distinct provenance path)", file=out)
    for sink in risks:
        print("  %s" % sink.label(), file=out)
        for argument in sink.arguments:
            print("    %s: %s: %s" % (_report_role(argument.role), argument.klass, argument.reason), file=out)
        for path in sink.pe_paths:
            print("    %s entries=%s" % (path["arguments"], ", ".join(base.short_key(each) for each in path["entries"])),
                  file=out)
            chain = path["chain"]
            print("      " + base.short_key(chain[0]), file=out)
            for element in chain[1:]:
                print("      -> " + base.short_key(element), file=out)
    for key, value in totals.items():
        print("%s=%d" % (key, value), file=out)
    if failures:
        print("bytecode-api-pe-guard: FAIL", file=out)
        for failure in failures:
            print("  " + failure, file=out)
        if candidate_path is not None:
            print("Candidate topology baseline (safe sites only; review before adopting): %s" % candidate_path,
                  file=out)
        return 1
    print("bytecode-api-pe-guard: PASS", file=out)
    for key in ACCEPTANCE_KEYS:
        print("  %s=0" % key, file=out)
    print("  topology baseline exact", file=out)
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    subparsers = parser.add_subparsers(dest="command")
    check_parser = subparsers.add_parser("check")
    check_parser.add_argument("--source", required=True, type=Path)
    check_parser.add_argument("--baseline", required=True, type=Path)
    check_parser.add_argument("--report", type=Path)
    check_parser.add_argument("--label-root", type=Path, default=Path("."))
    arguments = parser.parse_args(argv)
    if arguments.command != "check":
        parser.print_help()
        return 2
    return check(arguments.source, arguments.baseline, arguments.report, arguments.label_root)


if __name__ == "__main__":
    sys.exit(main())
