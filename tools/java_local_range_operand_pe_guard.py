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


"""TEST009-A static LocalRangeAccessor receiver/BytecodeNode PE guard.

Every indexed LocalRangeAccessor operation (isCleared/getObject/setObject/
clear and the typed get*/set* family) requires, during partial evaluation,
that its receiver (the accessor itself) and its BytecodeNode argument are
partial-evaluation constants, independently of the index. The index
dimension is owned by tools/java_local_range_pe_guard.py; this guard owns
only the other two and reuses that guard's lexer, parser, sink inventory,
and conservative call graph.

Local receiver provenance at the sink:

  CONSTANT_OPERAND_RECEIVER  positional @ConstantOperand(type =
                             LocalRangeAccessor.class) of the enclosing
                             Bytecode DSL operation specialization
  HELPER_PARAMETER_RECEIVER  ordinary method parameter (followed through
                             callers)
  FIELD_DERIVED_RECEIVER     read from a field or another object; final or
                             not, never proven
  UNKNOWN_RECEIVER           not reliably classified

Local BytecodeNode provenance at the sink:

  BOUND_BYTECODE_NODE                    @Bind("$bytecodeNode") parameter of
                                         the enclosing operation specialization
  HELPER_PARAMETER_BYTECODE_NODE         ordinary method parameter (followed)
  CURRENT_ROOT_DERIVED_BYTECODE_NODE     x.getBytecodeNode(), directly or via a
                                         zero-argument helper returning it
  FIELD_OR_OBJECT_DERIVED_BYTECODE_NODE  field, other-object, or call result
  UNKNOWN_BYTECODE_NODE                  not reliably classified

Helper parameters are followed backwards over the conservative call graph to
Bytecode DSL operation roots, both dimensions per path. Only the two first
classes are proofs, and only when supplied by the operation root itself. An
operation root that is also called directly as a helper is followed through
those callers too. @TruffleBoundary cuts PE traversal; an unresolved edge
that can connect a root to a sink is PE_REACHABILITY_UNKNOWN. A call
argument originating inside a lambda or anonymous-class body is never proven.

Per sink:

  PE_REACHABLE         reached from a root; receiver and BytecodeNode are
                       each reported PROVEN (every path) or RISK
  BOUNDARY_CUT         every otherwise relevant path is cut by @TruffleBoundary
  NOT_PE_REACHABLE     no path from a known Bytecode DSL operation root
  PE_REACHABILITY_UNKNOWN

The guard fails on any PE-reachable receiver or BytecodeNode risk, on any
PE_REACHABILITY_UNKNOWN, and on any UNKNOWN local provenance outside a
@TruffleBoundary method. Risks are never baselineable. The checked-in
topology baseline records every determinate safe sink exactly; a new, drifted,
duplicate, or stale entry fails the guard.

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

ACCESSOR_TYPE = base.ACCESSOR_TYPE
BYTECODE_NODE_TYPE = "BytecodeNode"
NODE_ARGUMENT = 0
BOUND_NODE_EXPRESSION = '"$bytecodeNode"'

RECEIVER_CONSTANT_OPERAND = "CONSTANT_OPERAND_RECEIVER"
RECEIVER_HELPER_PARAMETER = "HELPER_PARAMETER_RECEIVER"
RECEIVER_FIELD_DERIVED = "FIELD_DERIVED_RECEIVER"
RECEIVER_UNKNOWN = "UNKNOWN_RECEIVER"
RECEIVER_CLASSES = (RECEIVER_CONSTANT_OPERAND, RECEIVER_HELPER_PARAMETER, RECEIVER_FIELD_DERIVED, RECEIVER_UNKNOWN)

NODE_BOUND = "BOUND_BYTECODE_NODE"
NODE_HELPER_PARAMETER = "HELPER_PARAMETER_BYTECODE_NODE"
NODE_ROOT_DERIVED = "CURRENT_ROOT_DERIVED_BYTECODE_NODE"
NODE_OBJECT_DERIVED = "FIELD_OR_OBJECT_DERIVED_BYTECODE_NODE"
NODE_UNKNOWN = "UNKNOWN_BYTECODE_NODE"
NODE_CLASSES = (NODE_BOUND, NODE_HELPER_PARAMETER, NODE_ROOT_DERIVED, NODE_OBJECT_DERIVED, NODE_UNKNOWN)

# Path provenance at a PE root, besides the determinate local classes.
PROV_ROOT_RUNTIME_OPERAND = "ROOT_RUNTIME_OPERAND"
PROV_UNPROVEN_ARGUMENT = "UNPROVEN_ARGUMENT"
PROVEN_RECEIVER = (RECEIVER_CONSTANT_OPERAND,)
PROVEN_NODE = (NODE_BOUND,)

PE_REACHABLE = "PE_REACHABLE"
BOUNDARY_CUT = base.BOUNDARY_CUT
NOT_PE_REACHABLE = base.NOT_PE_REACHABLE
PE_REACHABILITY_UNKNOWN = base.PE_REACHABILITY_UNKNOWN
PE_CLASSES = (PE_REACHABLE, BOUNDARY_CUT, NOT_PE_REACHABLE, PE_REACHABILITY_UNKNOWN)
PROVEN = "PROVEN"
RISK = "RISK"

BASELINE_SCHEMA = "protos-local-range-operand-pe-baseline-v1"
REPORT_SCHEMA = "protos-local-range-operand-pe-report-v1"
CANDIDATE_NAME = "local-range-operand-pe-baseline.candidate.json"
IDENTITY_KEYS = ("path", "class", "method", "operation", "receiver", "bytecode_node", "occurrence")
BASELINE_KEYS = IDENTITY_KEYS + ("receiver_class", "bytecode_node_class", "pe_reachability")

GuardError = base.GuardError


class OperandSink(object):
    def __init__(self, inventory):
        self.inventory = inventory  # the index guard's Sink for the same call
        self.path = inventory.path
        self.line = inventory.line
        self.type_name = inventory.type_name
        self.method = inventory.method
        self.operation = inventory.operation
        self.receiver = inventory.receiver
        self.bytecode_node = "<unavailable>"
        self.occurrence = 0
        self.receiver_class = RECEIVER_UNKNOWN
        self.receiver_reason = ""
        self.node_class = NODE_UNKNOWN
        self.node_reason = ""
        self.boundary = False
        self._receiver_param = None
        self._node_param = None
        self.pe_reachability = None
        self.receiver_pe = None
        self.node_pe = None
        self.pe_paths = []
        self.pe_call_chain = []
        self.pe_unknown = []
        self.status = None

    def identity(self):
        return (self.path, self.type_name, self.method, self.operation, self.receiver, self.bytecode_node,
                self.occurrence)

    def local_unknown(self):
        return self.receiver_class == RECEIVER_UNKNOWN or self.node_class == NODE_UNKNOWN

    def entry(self):
        entry = dict(zip(IDENTITY_KEYS, self.identity()))
        entry["receiver_class"] = self.receiver_class
        entry["bytecode_node_class"] = self.node_class
        entry["pe_reachability"] = self.pe_reachability
        return entry

    def label(self):
        return "%s:%d %s.%s %s.%s(%s, ...)" % (
            self.path, self.line, self.type_name, self.method, self.receiver, self.operation, self.bytecode_node)

    def to_json(self):
        document = self.entry()
        document.update({
            "line": self.line,
            "receiver_reason": self.receiver_reason,
            "bytecode_node_reason": self.node_reason,
            "enclosing_truffle_boundary": self.boundary,
            "receiver_pe": self.receiver_pe,
            "bytecode_node_pe": self.node_pe,
            "pe_paths": [dict(path) for path in self.pe_paths],
            "pe_call_chain": list(self.pe_call_chain),
            "pe_unknown": list(self.pe_unknown),
            "status": self.status,
        })
        return document


# --------------------------------------------------------------------------
# Local provenance
# --------------------------------------------------------------------------


def _single_id(java, start, end):
    return end - start == 1 and java.is_id(start) and java.text(start) not in base.KEYWORDS


def _this_field(java, start, end):
    return end - start == 3 and java.text(start) == "this" and java.is_op(start + 1, ".") and java.is_id(start + 2)


def _qualified_chain(java, start, end):
    """True for a.b.c (identifiers and dots only, at least one dot)."""
    if end - start < 3 or (end - start) % 2 == 0:
        return False
    for index in range(start, end):
        if (index - start) % 2 == 0 and not java.is_id(index):
            return False
        if (index - start) % 2 == 1 and not java.is_op(index, "."):
            return False
    return True


def _is_root_specialization(method):
    names = base._annotation_names(method.annotations)
    return "Specialization" in names and method.owner is not None and "Operation" in base._annotation_names(
        method.owner.annotations)


def prove_constant_receiver(java, method, param) -> Optional[str]:
    """The positional LocalRangeAccessor @ConstantOperand proof, or None."""
    if not _is_root_specialization(method) or param.type_name != ACCESSOR_TYPE:
        return None
    operands = base._constant_operands(java, method.owner)
    if not operands:
        return None
    position = method.params.index(param)
    if position >= len(operands):
        return None
    for leading in method.params[: position + 1]:
        if leading.annotations:
            return None
    operand_type, operand_name = operands[position]
    if operand_type != ACCESSOR_TYPE + ".class" or operand_name != param.name:
        return None
    return "positional @ConstantOperand #%d (type = %s.class, name = \"%s\") of @Operation %s" % (
        position, ACCESSOR_TYPE, operand_name, method.owner.qualified)


def prove_bound_node(java, method, param) -> Optional[str]:
    """The @Bind("$bytecodeNode") operation-specialization proof, or None."""
    if not _is_root_specialization(method) or param.type_name != BYTECODE_NODE_TYPE:
        return None
    for name, start, end in param.annotations:
        if name != "Bind" or not java.is_op(end, ")"):
            continue
        opener = java.matches[end]
        if end - opener == 2 and java.text(opener + 1) == BOUND_NODE_EXPRESSION:
            return "@Bind(%s) parameter of @Operation %s" % (BOUND_NODE_EXPRESSION, method.owner.qualified)
    return None


def _parameter(java, method, declaration, prove, proven_class, helper_class, unknown_class, found):
    written = base._reassigned(java, declaration.name, method.body_open, method.body_close)
    if written is not None:
        return unknown_class, "parameter '%s' is reassigned at line %d" % (declaration.name, java.tokens[written].line)
    # A proven root parameter is still tracked: the root may also be called directly as a helper.
    found.append(declaration)
    proof = prove(java, method, declaration)
    if proof is not None:
        return proven_class, "parameter '%s' is the %s" % (declaration.name, proof)
    return helper_class, "ordinary %s parameter '%s'" % (declaration.type_name, declaration.name)


def classify_receiver(java, method, start, end, at, found, seen=frozenset()):
    """Classifies the receiver expression tokens [start, end); appends a helper parameter to 'found'."""
    if _this_field(java, start, end):
        return RECEIVER_FIELD_DERIVED, "receiver read from field 'this.%s'" % java.text(start + 2)
    if _qualified_chain(java, start, end):
        return RECEIVER_FIELD_DERIVED, "receiver read from another object: %s" % base.normalize(java, start, end)
    if not _single_id(java, start, end):
        return RECEIVER_UNKNOWN, "compound receiver expression %s" % base.normalize(java, start, end)
    name = java.text(start)
    if name in seen or len(seen) > 8:
        return RECEIVER_UNKNOWN, "cyclic or too deep local alias chain at '%s'" % name
    declaration = java.lookup(method, name, at)
    if declaration is None:
        return RECEIVER_UNKNOWN, "'%s' does not resolve to a local, parameter, or field" % name
    if declaration.kind == "param":
        return _parameter(java, method, declaration, prove_constant_receiver, RECEIVER_CONSTANT_OPERAND,
                          RECEIVER_HELPER_PARAMETER, RECEIVER_UNKNOWN, found)
    if declaration.kind == "field":
        modifiers = " ".join(sorted(declaration.modifiers)) or "non-final"
        return RECEIVER_FIELD_DERIVED, "receiver read from %s field '%s'; a field is never a proof" % (
            modifiers, name)
    if declaration.kind != "local":
        return RECEIVER_UNKNOWN, "loop variable '%s'" % name
    return _local_alias(java, method, declaration, found, seen | {name}, classify_receiver, RECEIVER_UNKNOWN)


def _local_alias(java, method, declaration, found, seen, classify, unknown_class):
    written = base._reassigned(java, declaration.name, declaration.index + 1, declaration.scope_end + 1,
                               skip={declaration.index})
    if written is not None:
        return unknown_class, "local '%s' is reassigned at line %d" % (declaration.name, java.tokens[written].line)
    if declaration.init is None:
        return unknown_class, "local '%s' has no initializer" % declaration.name
    start, end = declaration.init
    classification, reason = classify(java, method, start, end, declaration.index, found, seen)
    return classification, "local '%s' = %s; %s" % (declaration.name, base.normalize(java, start, end), reason)


def _call_name(java, start, end):
    """(name, dot index or None) for an expression ending in a zero-argument call."""
    if end - start < 3 or not java.is_op(end - 1, ")") or java.matches[end - 1] != end - 2:
        return None
    name_index = end - 3
    if not java.is_id(name_index) or name_index < start:
        return None
    if base._chain_start(java, end - 1) != start:
        return None
    dot = name_index - 1 if name_index > start and java.is_op(name_index - 1, ".") else None
    return java.text(name_index), dot


def _single_return(java, helper):
    """[start, end) of the expression of a body consisting of exactly 'return <expr>;'."""
    first = helper.body_open + 1
    if helper.body_open < 0 or java.text(first) != "return":
        return None
    end = java._statement_end(first + 1, helper.body_close)
    if not java.is_op(end, ";") or end + 1 != helper.body_close or end == first + 1:
        return None
    return first + 1, end


def classify_node(java, method, start, end, at, found, seen=frozenset()):
    """Classifies the BytecodeNode argument tokens [start, end); appends a helper parameter to 'found'."""
    if _this_field(java, start, end) or _qualified_chain(java, start, end):
        return NODE_OBJECT_DERIVED, "BytecodeNode read from %s" % base.normalize(java, start, end)
    call = _call_name(java, start, end)
    if call is not None:
        name, dot = call
        if name == "getBytecodeNode":
            return NODE_ROOT_DERIVED, "BytecodeNode re-derived from runtime state: %s" % base.normalize(
                java, start, end)
        if dot is None and len(seen) <= 8 and ("()" + name) not in seen:
            helpers = _zero_arity_methods(method, name)
            if len(helpers) == 1 and helpers[0].java is java:
                body = _single_return(java, helpers[0])
                if body is not None:
                    classification, reason = classify_node(java, helpers[0], body[0], body[1], body[0], [],
                                                           seen | {"()" + name})
                    if classification == NODE_ROOT_DERIVED:
                        return NODE_ROOT_DERIVED, "%s() returns %s" % (name, reason)
        return NODE_OBJECT_DERIVED, "BytecodeNode returned by call %s" % base.normalize(java, start, end)
    if not _single_id(java, start, end):
        return NODE_UNKNOWN, "compound BytecodeNode expression %s" % base.normalize(java, start, end)
    name = java.text(start)
    if name in seen or len(seen) > 8:
        return NODE_UNKNOWN, "cyclic or too deep local alias chain at '%s'" % name
    declaration = java.lookup(method, name, at)
    if declaration is None:
        return NODE_UNKNOWN, "'%s' does not resolve to a local, parameter, or field" % name
    if declaration.kind == "param":
        return _parameter(java, method, declaration, prove_bound_node, NODE_BOUND, NODE_HELPER_PARAMETER,
                          NODE_UNKNOWN, found)
    if declaration.kind == "field":
        return NODE_OBJECT_DERIVED, "BytecodeNode read from field '%s'" % name
    if declaration.kind != "local":
        return NODE_UNKNOWN, "loop variable '%s'" % name
    return _local_alias(java, method, declaration, found, seen | {name}, classify_node, NODE_UNKNOWN)


def _zero_arity_methods(method, name):
    info = method.owner
    while info is not None:
        matches = [each for each in info.methods if each.name == name and not each.params and not each.abstract]
        if matches:
            return matches
        info = info.outer
    return []


# --------------------------------------------------------------------------
# Inventory
# --------------------------------------------------------------------------


def _arguments(java, index):
    if not java.is_op(index + 1, "("):
        return None
    return java.split_top_level(index + 2, java.matches[index + 1])


def _receiver_range(java, dot):
    """[start, end) token range of the receiver expression before 'dot'."""
    return base._chain_start(java, dot - 1), dot


def classify_sink(sink: OperandSink):
    inventory = sink.inventory
    java = inventory._java
    method = inventory._method
    index = inventory.column_index
    if java is None or method is None:
        sink.receiver_reason = sink.node_reason = "access outside a method body"
        return
    sink.boundary = base.is_truffle_boundary(method)
    if java.is_op(index - 1, "::"):
        sink.receiver_reason = sink.node_reason = "method reference to an indexed %s operation" % ACCESSOR_TYPE
        return
    if any(start < index <= end for start, end in method.opaque):
        sink.receiver_reason = sink.node_reason = "access inside a lambda or anonymous class body"
        return
    arguments = _arguments(java, index)
    if not arguments or not arguments[NODE_ARGUMENT]:
        sink.receiver_reason = sink.node_reason = "missing BytecodeNode argument"
        return
    node_tokens = arguments[NODE_ARGUMENT]
    sink.bytecode_node = base.normalize(java, node_tokens[0], node_tokens[-1] + 1)
    start, end = _receiver_range(java, index - 1)
    found = []
    if inventory.reason.startswith("receiver '"):
        sink.receiver_reason = inventory.reason
    else:
        sink.receiver_class, sink.receiver_reason = classify_receiver(java, method, start, end, index, found)
        sink._receiver_param = found[-1] if found else None
    found = []
    sink.node_class, sink.node_reason = classify_node(java, method, node_tokens[0], node_tokens[-1] + 1, index,
                                                      found)
    sink._node_param = found[-1] if found else None


def build_sinks(inventory) -> List[OperandSink]:
    sinks = [OperandSink(each) for each in inventory]
    for sink in sinks:
        classify_sink(sink)
    occurrences = {}
    for sink in sinks:
        identity = sink.identity()[:-1]
        sink.occurrence = occurrences.get(identity, 0)
        occurrences[identity] = sink.occurrence + 1
    return sinks


# --------------------------------------------------------------------------
# PE reachability over both dimensions
# --------------------------------------------------------------------------


def _start_dimension(classification, param, method):
    if param is not None and param in method.params:
        return ("param", method.params.index(param))
    return ("prov", classification)


def _root_dimension(root, dimension, prove, proven_class):
    if dimension[0] == "prov":
        return dimension[1]
    proof = prove(root.java, root, root.params[dimension[1]])
    return proven_class if proof is not None else PROV_ROOT_RUNTIME_OPERAND


def _argument_dimension(site, target, dimension, classify, helper_class, proven_class):
    if dimension[0] == "prov":
        return dimension
    tracked = dimension[1]
    caller = site.caller
    if site.args is None or caller.body_open < 0 or site.method is None or site.opaque:
        return ("prov", PROV_UNPROVEN_ARGUMENT)
    if target.varargs and tracked >= len(target.params) - 1:
        return ("prov", PROV_UNPROVEN_ARGUMENT)
    if tracked >= len(site.args) or not site.args[tracked]:
        return ("prov", PROV_UNPROVEN_ARGUMENT)
    argument = site.args[tracked]
    found = []
    classification, _ = classify(site.java, caller, argument[0], argument[-1] + 1, site.index, found)
    if classification in (helper_class, proven_class):
        if found and found[-1] in caller.params:
            return ("param", caller.params.index(found[-1]))
        return ("prov", PROV_UNPROVEN_ARGUMENT)
    return ("prov", classification)


def classify_reachability(graph, sink: OperandSink):
    method = sink.inventory._method
    sink.pe_paths = []
    sink.pe_call_chain = []
    sink.pe_unknown = []
    if method is None or (sink.local_unknown() and not sink.boundary):
        sink.pe_reachability = PE_REACHABILITY_UNKNOWN
        sink.pe_unknown = ["local provenance is UNKNOWN: receiver: %s; BytecodeNode: %s" % (
            sink.receiver_reason, sink.node_reason)]
        return
    if sink.boundary:
        sink.pe_reachability = BOUNDARY_CUT
        sink.pe_call_chain = [base.method_key(method)]
        return
    receiver = _start_dimension(sink.receiver_class, sink._receiver_param, method)
    node = _start_dimension(sink.node_class, sink._node_param, method)
    first = ((method, receiver, node), False)
    parent = {first: None}
    queue = deque([first])
    terminals = {}
    roots = {}
    cuts = []
    unknown = []

    def add_unknown(reason):
        graph.unknown_edges.add(reason.split("; reaches PE root", 1)[0])
        if reason not in unknown:
            unknown.append(reason)

    while queue:
        current_node = queue.popleft()
        (current, receiver, node), uncertain = current_node
        if base.is_pe_root(current):
            key = (_root_dimension(current, receiver, prove_constant_receiver, RECEIVER_CONSTANT_OPERAND),
                   _root_dimension(current, node, prove_bound_node, NODE_BOUND))
            if uncertain:
                add_unknown("%s; reaches PE root %s" % (
                    graph._uncertain_reason(parent, current_node), base.method_key(current)))
            else:
                if key not in terminals:
                    terminals[key] = graph._chain(parent, current_node)
                roots.setdefault(key, set()).add(base.method_key(current))
            # A root may also be invoked directly as a helper: keep following its callers.
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
            state = (
                caller,
                _argument_dimension(site, current, receiver, classify_receiver, RECEIVER_HELPER_PARAMETER,
                                    RECEIVER_CONSTANT_OPERAND),
                _argument_dimension(site, current, node, classify_node, NODE_HELPER_PARAMETER, NODE_BOUND),
            )
            following = (state, edge_uncertain)
            if following in parent or (edge_uncertain and (state, False) in parent):
                continue
            parent[following] = (current_node, reason if status == base.EDGE_UNCERTAIN else None, site)
            if len(parent) > base.SEARCH_STATE_LIMIT:
                add_unknown("reachability search exceeded %d states" % base.SEARCH_STATE_LIMIT)
                queue.clear()
                break
            queue.append(following)
    ordered = sorted(terminals, key=lambda key: (key[0] not in PROVEN_RECEIVER, key[1] not in PROVEN_NODE, key))
    sink.pe_paths = [
        {"receiver": key[0], "bytecode_node": key[1], "roots": len(roots[key]), "chain": terminals[key]}
        for key in ordered
    ]
    sink.pe_unknown = unknown
    if unknown:
        sink.pe_reachability = PE_REACHABILITY_UNKNOWN
    elif terminals:
        sink.pe_reachability = PE_REACHABLE
        sink.receiver_pe = PROVEN if all(key[0] in PROVEN_RECEIVER for key in terminals) else RISK
        sink.node_pe = PROVEN if all(key[1] in PROVEN_NODE for key in terminals) else RISK
        risky = [key for key in terminals if key[0] not in PROVEN_RECEIVER or key[1] not in PROVEN_NODE]
        sink.pe_call_chain = min((terminals[key] for key in (risky or terminals)), key=len)
    elif cuts:
        sink.pe_reachability = BOUNDARY_CUT
        sink.pe_call_chain = cuts[0]
    else:
        sink.pe_reachability = NOT_PE_REACHABLE


def analyze(parsed_files, inventory):
    sinks = build_sinks(inventory)
    graph = base.CallGraph(parsed_files)
    for sink in sinks:
        classify_reachability(graph, sink)
    return sinks, graph


def analyze_sources(items):
    """Analyzes (label, source) pairs; returns (sinks, call graph)."""
    inventory, parsed = base.parse_sources(items)
    return analyze(parsed, inventory)


# --------------------------------------------------------------------------
# Verification
# --------------------------------------------------------------------------

STATUS_SAFE = "BASELINED_SAFE"
STATUS_RISK = "PE_REACHABLE_RISK"
STATUS_UNKNOWN = "UNKNOWN"
STATUS_NEW = "NEW_UNBASELINED_SITE"
STATUS_DRIFT = "TOPOLOGY_DRIFT"


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
            if key == "occurrence":
                if not isinstance(value, int) or isinstance(value, bool) or value < 0:
                    raise GuardError("baseline entry %d: occurrence must be a non-negative integer" % position)
            elif not isinstance(value, str) or not value or "*" in value:
                raise GuardError("baseline entry %d: %s must be a non-empty exact (non-wildcard) string" % (
                    position, key))
        if entry["receiver_class"] not in RECEIVER_CLASSES or entry["bytecode_node_class"] not in NODE_CLASSES:
            raise GuardError("baseline entry %d: unknown local provenance class" % position)
        if entry["pe_reachability"] not in (PE_REACHABLE, BOUNDARY_CUT, NOT_PE_REACHABLE):
            raise GuardError("baseline entry %d: pe_reachability %r is not baselineable" % (
                position, entry["pe_reachability"]))
        if entry["pe_reachability"] == PE_REACHABLE and (
                entry["receiver_class"] not in PROVEN_RECEIVER + (RECEIVER_HELPER_PARAMETER,)
                or entry["bytecode_node_class"] not in PROVEN_NODE + (NODE_HELPER_PARAMETER,)):
            raise GuardError("baseline entry %d: a PE-reachable non-proven provenance is a risk and is never "
                             "baselineable" % position)
    return entries


def verify(sinks: List[OperandSink], entries: List[dict]) -> List[str]:
    failures = []
    for sink in sinks:
        if sink.pe_reachability == PE_REACHABILITY_UNKNOWN:
            sink.status = STATUS_UNKNOWN
            failures.append("PE_REACHABILITY_UNKNOWN %s: %s" % (sink.label(), " | ".join(
                sink.pe_unknown[:5]) + (" | ..." if len(sink.pe_unknown) > 5 else "")))
        elif sink.pe_reachability == PE_REACHABLE and RISK in (sink.receiver_pe, sink.node_pe):
            sink.status = STATUS_RISK
            for dimension, value in (("RECEIVER", sink.receiver_pe), ("BYTECODE_NODE", sink.node_pe)):
                if value == RISK:
                    failures.append("PE_REACHABLE_%s_RISK %s via %s" % (
                        dimension, sink.label(), " -> ".join(base.short_key(each) for each in sink.pe_call_chain)))
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
    totals = {"TOTAL_LOCAL_RANGE_SINKS": len(sinks)}
    for classification in RECEIVER_CLASSES:
        totals[classification] = sum(1 for sink in sinks if sink.receiver_class == classification)
    for classification in NODE_CLASSES:
        totals[classification] = sum(1 for sink in sinks if sink.node_class == classification)
    reachable = [sink for sink in sinks if sink.pe_reachability == PE_REACHABLE]
    totals[PE_REACHABLE] = len(reachable)
    totals["PE_REACHABLE_RECEIVER_PROVEN"] = sum(1 for sink in reachable if sink.receiver_pe == PROVEN)
    totals["PE_REACHABLE_RECEIVER_RISK"] = sum(1 for sink in reachable if sink.receiver_pe == RISK)
    totals["PE_REACHABLE_BYTECODE_NODE_PROVEN"] = sum(1 for sink in reachable if sink.node_pe == PROVEN)
    totals["PE_REACHABLE_BYTECODE_NODE_RISK"] = sum(1 for sink in reachable if sink.node_pe == RISK)
    for classification in (BOUNDARY_CUT, NOT_PE_REACHABLE, PE_REACHABILITY_UNKNOWN):
        totals[classification] = sum(1 for sink in sinks if sink.pe_reachability == classification)
    totals["PE_ROOTS"] = len(graph.roots)
    totals["CALL_GRAPH_AMBIGUITIES"] = len(graph.unknown_edges)
    totals["UNPARSED_FILES"] = len(graph.unparsed)
    for status in (STATUS_NEW, STATUS_DRIFT):
        totals[status] = sum(1 for sink in sinks if sink.status == status)
    return totals


def check(source: Path, baseline: Path, report: Optional[Path], label_root: Path, out=sys.stdout) -> int:
    try:
        inventory, parsed = base.scan_tree(source, label_root)
        entries = load_baseline(baseline)
    except GuardError as error:
        print("local-range-operand-pe-guard: ANALYSIS_FAILED: %s" % error, file=out)
        return 1
    sinks, graph = analyze(parsed, inventory)
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
            "description": "Exact topology of every determinate PE-safe LocalRangeAccessor sink "
                           "(receiver and BytecodeNode provenance). Risks are never baselined.",
            "entries": candidate,
        }, indent=2) + "\n", encoding="utf-8")
    print("local-range-operand-pe-guard: %s receiver/BytecodeNode provenance" % ACCESSOR_TYPE, file=out)
    print("  %-24s %-34s %-37s %-23s %s" % ("STATUS", "RECEIVER", "BYTECODE_NODE", "PE", "SINK"), file=out)
    for sink in sinks:
        pe = sink.pe_reachability
        if pe == PE_REACHABLE:
            pe = "%s(r=%s,n=%s)" % (pe, sink.receiver_pe, sink.node_pe)
        print("  %-24s %-34s %-37s %-23s %s:%d %s.%s %s.%s(%s, ...)" % (
            sink.status, sink.receiver_class, sink.node_class, pe, sink.path.rsplit("/", 1)[-1], sink.line,
            sink.type_name.rsplit(".", 1)[-1], sink.method.split("(", 1)[0], sink.receiver, sink.operation,
            sink.bytecode_node), file=out)
    risks = [sink for sink in sinks if sink.status == STATUS_RISK]
    if risks:
        print("local-range-operand-pe-guard: PE-reachable risks (every distinct provenance path)", file=out)
    for sink in risks:
        print("  %s" % sink.label(), file=out)
        print("    receiver: %s: %s" % (sink.receiver_class, sink.receiver_reason), file=out)
        print("    BytecodeNode: %s: %s" % (sink.node_class, sink.node_reason), file=out)
        for path in sink.pe_paths:
            print("    receiver=%s bytecode_node=%s roots=%d" % (
                path["receiver"], path["bytecode_node"], path["roots"]), file=out)
            chain = path["chain"]
            print("      " + base.short_key(chain[0]), file=out)
            for element in chain[1:]:
                print("      -> " + base.short_key(element), file=out)
    for key, value in totals.items():
        print("%s=%d" % (key, value), file=out)
    if failures:
        print("local-range-operand-pe-guard: FAIL", file=out)
        for failure in failures:
            print("  " + failure, file=out)
        if candidate_path is not None:
            print("Candidate topology baseline (safe sites only; review before adopting): %s" % candidate_path,
                  file=out)
        return 1
    print("local-range-operand-pe-guard: PASS", file=out)
    print("  PE_REACHABLE_RECEIVER_RISK=0", file=out)
    print("  PE_REACHABLE_BYTECODE_NODE_RISK=0", file=out)
    print("  PE_REACHABILITY_UNKNOWN=0", file=out)
    print("  topology baseline exact", file=out)
    print("NOTE: index/offset constancy is owned by tools/java_local_range_pe_guard.py", file=out)
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
