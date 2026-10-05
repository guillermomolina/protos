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


"""TEST009-B static LocalAccessor/MaterializedLocalAccessor PE guard.

At oracle/graal@95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b every LocalAccessor
operation (get*/set* typed family, clear, isCleared, getLocalName,
getLocalInfo) asserts during partial evaluation that its receiver and its
BytecodeNode argument are PE constants. Every MaterializedLocalAccessor
operation additionally asserts that the declaring BytecodeNode it derives,

  bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex)
      .getBytecodeNode()

is a PE constant. Sinks are inventoried by the inferred static type of the
receiver of every call named like such an operation, never by a list of
Protos methods; a candidate whose receiver type cannot be inferred fails.

The lexer, parser, conservative call graph, and the local receiver/
BytecodeNode provenance rules are those of the TEST009-A guards
(tools/java_local_range_pe_guard.py, tools/java_local_range_operand_pe_guard.py):
only a positional @ConstantOperand of the accessor's own type and an
@Bind("$bytecodeNode") (or bare @Bind BytecodeNode) parameter of a Bytecode
DSL operation specialization are proofs; helper parameters are followed backwards to every PE root; a
field (final or not), another object's state, or x.getBytecodeNode() never
is. @TruffleBoundary cuts PE traversal; an unresolved edge that can connect a
root to a sink is PE_REACHABILITY_UNKNOWN.

Declaring BytecodeNode (MaterializedLocalAccessor only), proven per PE path
only when all of the following hold at the root reached by that path:

  - the receiver is that root's positional MaterializedLocalAccessor
    @ConstantOperand and the supplied BytecodeNode is that same root's
    @Bind("$bytecodeNode") or bare @Bind, carried jointly along that one path, so both
    belong to the same executing bytecode root;
  - the @Operation is declared inside a @GenerateBytecode root class, so the
    operand value is produced only by that root's generated builder; and
  - every builder emission (begin<Operation>/emit<Operation>) passes a
    BytecodeLocal at the accessor's operand position, so the accessor is
    created by the builder of the shared BytecodeRootNodes group from a local
    of a root of that group, which is what its rootIndex encodes.

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

LOCAL_ACCESSOR = "LocalAccessor"
MATERIALIZED_ACCESSOR = "MaterializedLocalAccessor"
ACCESSOR_TYPES = (LOCAL_ACCESSOR, MATERIALIZED_ACCESSOR)
FAMILY_UNRESOLVED = "UNRESOLVED_RECEIVER_TYPE"
BYTECODE_LOCAL_TYPE = "BytecodeLocal"

# Operation name -> argument count; the BytecodeNode is always argument 0.
OPERATION_ARITY = {
    "getObject": 2, "getBoolean": 2, "getByte": 2, "getInt": 2, "getLong": 2, "getFloat": 2, "getDouble": 2,
    "setObject": 3, "setBoolean": 3, "setByte": 3, "setInt": 3, "setLong": 3, "setFloat": 3, "setDouble": 3,
    "clear": 2, "isCleared": 2, "getLocalName": 1, "getLocalInfo": 1,
}
NODE_ARGUMENT = 0

RECEIVER_CONSTANT_OPERAND = operand.RECEIVER_CONSTANT_OPERAND
RECEIVER_HELPER_PARAMETER = operand.RECEIVER_HELPER_PARAMETER
RECEIVER_FIELD_DERIVED = operand.RECEIVER_FIELD_DERIVED
RECEIVER_UNKNOWN = operand.RECEIVER_UNKNOWN
RECEIVER_CLASSES = operand.RECEIVER_CLASSES
NODE_BOUND = operand.NODE_BOUND
NODE_HELPER_PARAMETER = operand.NODE_HELPER_PARAMETER
NODE_UNKNOWN = operand.NODE_UNKNOWN
NODE_CLASSES = operand.NODE_CLASSES
PROV_ROOT_RUNTIME_OPERAND = operand.PROV_ROOT_RUNTIME_OPERAND

# Declaring-BytecodeNode provenance at a PE root (MaterializedLocalAccessor).
DECLARING_SAME_ROOT_GROUP = "SAME_ROOT_GROUP"
DECLARING_UNPROVEN_OPERANDS = "UNPROVEN_ACCESSOR_OR_BYTECODE_NODE"
DECLARING_NOT_GENERATED_ROOT = "OPERATION_OUTSIDE_GENERATE_BYTECODE_ROOT"
DECLARING_UNPROVEN_EMISSION = "EMISSION_NOT_FROM_BYTECODE_LOCAL"
NOT_APPLICABLE = "N/A"

PE_REACHABLE_PROVEN = "PE_REACHABLE_PROVEN"
PE_REACHABLE_RISK = "PE_REACHABLE_RISK"
BOUNDARY_CUT = base.BOUNDARY_CUT
NOT_PE_REACHABLE = base.NOT_PE_REACHABLE
PE_REACHABILITY_UNKNOWN = base.PE_REACHABILITY_UNKNOWN
PE_CLASSES = (PE_REACHABLE_PROVEN, PE_REACHABLE_RISK, BOUNDARY_CUT, NOT_PE_REACHABLE, PE_REACHABILITY_UNKNOWN)
PROVEN = "PROVEN"
RISK = "RISK"

BASELINE_SCHEMA = "protos-local-accessor-pe-baseline-v1"
REPORT_SCHEMA = "protos-local-accessor-pe-report-v1"
CANDIDATE_NAME = "local-accessor-pe-baseline.candidate.json"
IDENTITY_KEYS = ("path", "class", "method", "family", "operation", "receiver", "bytecode_node", "occurrence")
BASELINE_KEYS = IDENTITY_KEYS + ("receiver_class", "bytecode_node_class", "pe_reachability")

GuardError = base.GuardError


class AccessorSink(object):
    def __init__(self, site, family):
        self.site = site
        self.family = family
        self.path = site.java.label
        self.line = site.line
        self.type_name = site.owner.qualified if site.owner is not None else "<unknown>"
        self.method = site.method.signature if site.method is not None else "<outside-method>"
        self.operation = site.name
        self.receiver = "<unavailable>"
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
        self.declaring_pe = None
        self.pe_paths = []
        self.pe_call_chain = []
        self.pe_unknown = []
        self.emission_failures = []
        self.status = None

    @property
    def materialized(self):
        return self.family == MATERIALIZED_ACCESSOR

    def identity(self):
        return (self.path, self.type_name, self.method, self.family, self.operation, self.receiver,
                self.bytecode_node, self.occurrence)

    def local_unknown(self):
        return (self.family == FAMILY_UNRESOLVED or self.receiver_class == RECEIVER_UNKNOWN
                or self.node_class == NODE_UNKNOWN)

    def entry(self):
        entry = dict(zip(IDENTITY_KEYS, self.identity()))
        entry["receiver_class"] = self.receiver_class
        entry["bytecode_node_class"] = self.node_class
        entry["pe_reachability"] = self.pe_reachability
        return entry

    def label(self):
        return "%s:%d %s.%s %s.%s(%s, ...) [%s]" % (
            self.path, self.line, self.type_name, self.method, self.receiver, self.operation, self.bytecode_node,
            self.family)

    def to_json(self):
        document = self.entry()
        document.update({
            "line": self.line,
            "receiver_reason": self.receiver_reason,
            "bytecode_node_reason": self.node_reason,
            "enclosing_truffle_boundary": self.boundary,
            "receiver_pe": self.receiver_pe,
            "bytecode_node_pe": self.node_pe,
            "declaring_bytecode_node_pe": self.declaring_pe,
            "pe_paths": [dict(path) for path in self.pe_paths],
            "pe_call_chain": list(self.pe_call_chain),
            "pe_unknown": list(self.pe_unknown),
            "builder_emission_failures": list(self.emission_failures),
            "status": self.status,
        })
        return document


# --------------------------------------------------------------------------
# Local provenance
# --------------------------------------------------------------------------


def _constant_operands(java, info):
    """Positional (type, name or None) @ConstantOperand list, or None if not provable."""
    operands = []
    for name, start, end in info.annotations:
        if name == "ConstantOperands":
            return None
        if name != "ConstantOperand":
            continue
        if not java.is_op(end, ")"):
            return None
        values = {}
        for part in java.split_top_level(java.matches[end] + 1, end):
            if len(part) < 3 or not java.is_id(part[0]) or not java.is_op(part[1], "="):
                return None
            values[java.text(part[0])] = base.normalize(java, part[2], part[-1] + 1)
        if "specifyAtEnd" in values or "type" not in values:
            return None
        operands.append((values["type"], values["name"].strip('"') if "name" in values else None))
    return operands


def generated_root_of(info):
    """The enclosing @GenerateBytecode root class of an @Operation class, or None."""
    outer = info.outer if info is not None else None
    while outer is not None:
        if "GenerateBytecode" in base._annotation_names(outer.annotations):
            return outer
        outer = outer.outer
    return None


def constant_operand_position(java, method, param, accessor_type):
    """Position of 'param' as the positional @ConstantOperand of type accessor_type, or None."""
    if not operand._is_root_specialization(method) or param.type_name != accessor_type:
        return None
    operands = _constant_operands(java, method.owner)
    if not operands:
        return None
    position = method.params.index(param)
    if position >= len(operands):
        return None
    for leading in method.params[: position + 1]:
        if leading.annotations:
            return None
    operand_type, operand_name = operands[position]
    if operand_type != accessor_type + ".class" or operand_name not in (None, param.name):
        return None
    return position


def receiver_prover(accessor_type):
    def prove(java, method, param) -> Optional[str]:
        position = constant_operand_position(java, method, param, accessor_type)
        if position is None:
            return None
        return "positional @ConstantOperand #%d (type = %s.class) of @Operation %s" % (
            position, accessor_type, method.owner.qualified)
    return prove


def receiver_classifier(accessor_type):
    prove = receiver_prover(accessor_type)

    def classify(java, method, start, end, at, found, seen=frozenset()):
        if operand._this_field(java, start, end):
            return RECEIVER_FIELD_DERIVED, "receiver read from field 'this.%s'" % java.text(start + 2)
        if operand._qualified_chain(java, start, end):
            return RECEIVER_FIELD_DERIVED, "receiver read from another object: %s" % base.normalize(java, start, end)
        if not operand._single_id(java, start, end):
            return RECEIVER_UNKNOWN, "compound receiver expression %s" % base.normalize(java, start, end)
        name = java.text(start)
        if name in seen or len(seen) > 8:
            return RECEIVER_UNKNOWN, "cyclic or too deep local alias chain at '%s'" % name
        declaration = java.lookup(method, name, at)
        if declaration is None:
            return RECEIVER_UNKNOWN, "'%s' does not resolve to a local, parameter, or field" % name
        if declaration.kind == "param":
            return operand._parameter(java, method, declaration, prove, RECEIVER_CONSTANT_OPERAND,
                                      RECEIVER_HELPER_PARAMETER, RECEIVER_UNKNOWN, found)
        if declaration.kind == "field":
            modifiers = " ".join(sorted(declaration.modifiers)) or "non-final"
            return RECEIVER_FIELD_DERIVED, "receiver read from %s field '%s'; a field is never a proof" % (
                modifiers, name)
        if declaration.kind != "local":
            return RECEIVER_UNKNOWN, "loop variable '%s'" % name
        return operand._local_alias(java, method, declaration, found, seen | {name}, classify, RECEIVER_UNKNOWN)

    return prove, classify


PROVERS = dict((name, receiver_classifier(name)) for name in ACCESSOR_TYPES)


def classify_sink(sink: AccessorSink):
    site = sink.site
    java = site.java
    method = site.method
    if method is None:
        sink.receiver_reason = sink.node_reason = "access outside a method body"
        return
    sink.boundary = base.is_truffle_boundary(method)
    start, end = operand._receiver_range(java, site.dot)
    sink.receiver = base.normalize(java, start, end)
    if site.args is None:
        sink.receiver_reason = sink.node_reason = "method reference to an accessor operation"
        return
    if site.args[NODE_ARGUMENT]:
        node_tokens = site.args[NODE_ARGUMENT]
        sink.bytecode_node = base.normalize(java, node_tokens[0], node_tokens[-1] + 1)
    if sink.family == FAMILY_UNRESOLVED:
        sink.receiver_reason = sink.node_reason = (
            "receiver '%s' type is not statically resolvable; it may be a %s" % (
                sink.receiver, " or ".join(ACCESSOR_TYPES)))
        return
    if site.opaque:
        sink.receiver_reason = sink.node_reason = "access inside a lambda or anonymous class body"
        return
    if not site.args[NODE_ARGUMENT]:
        sink.receiver_reason = sink.node_reason = "missing BytecodeNode argument"
        return
    found = []
    sink.receiver_class, sink.receiver_reason = PROVERS[sink.family][1](java, method, start, end, site.index, found)
    sink._receiver_param = found[-1] if found else None
    found = []
    sink.node_class, sink.node_reason = operand.classify_node(java, method, node_tokens[0], node_tokens[-1] + 1,
                                                              site.index, found)
    sink._node_param = found[-1] if found else None


# --------------------------------------------------------------------------
# Inventory by receiver type
# --------------------------------------------------------------------------


def _family_of(receiver):
    """Accessor family of an inferred receiver type, None when it is certainly not one, or unresolved."""
    if receiver is None:
        return FAMILY_UNRESOLVED
    if receiver.kind == base.T_KNOWN:
        return receiver.name if receiver.name in ACCESSOR_TYPES else None
    if receiver.kind == base.T_STATIC:
        return FAMILY_UNRESOLVED if receiver.name in ACCESSOR_TYPES else None
    if receiver.kind == base.T_SUPER:
        return FAMILY_UNRESOLVED if receiver.ids & set(ACCESSOR_TYPES) else None
    # T_EXTERNAL: a member inherited from an external supertype; its ids name
    # those supertypes, not the member's result type, which may be an accessor.
    return FAMILY_UNRESOLVED


def inventory(graph) -> List[AccessorSink]:
    sinks = []
    for name, arity in sorted(OPERATION_ARITY.items()):
        for site in graph.sites_by_name.get(name, ()):
            if site.dot is None or site.kind not in ("call", "ref"):
                continue
            if site.args is not None and len(site.args) != arity:
                continue
            family = _family_of(graph.receiver_type(site))
            if family is None:
                continue
            sinks.append(AccessorSink(site, family))
    sinks.sort(key=lambda sink: (sink.path, sink.line, sink.site.index))
    for sink in sinks:
        classify_sink(sink)
    occurrences = {}
    for sink in sinks:
        identity = sink.identity()[:-1]
        sink.occurrence = occurrences.get(identity, 0)
        occurrences[identity] = sink.occurrence + 1
    return sinks


# --------------------------------------------------------------------------
# Builder emissions of MaterializedLocalAccessor operations
# --------------------------------------------------------------------------


class Emissions(object):
    """Per @Operation class: does every builder emission supply a BytecodeLocal accessor operand?"""

    def __init__(self, graph):
        self.graph = graph
        self._cache = {}

    def check(self, root, position):
        info = root.owner
        key = (id(info), position)
        if key not in self._cache:
            self._cache[key] = self._check(info, position)
        return self._cache[key]

    def _check(self, info, position):
        failures = []
        count = 0
        for prefix in ("begin", "emit"):
            for site in self.graph.sites_by_name.get(prefix + info.name, ()):
                if site.args is None or site.kind != "call":
                    failures.append("%s: %s%s is not a direct builder call" % (site.location(), prefix, info.name))
                    continue
                count += 1
                if position >= len(site.args) or not site.args[position]:
                    failures.append("%s: %s%s has no accessor operand #%d" % (
                        site.location(), prefix, info.name, position))
                    continue
                argument = site.args[position]
                value = self.graph.type_expr(site.java, site.method, site.owner, argument[0], argument[-1] + 1)
                if value is None or value.kind != base.T_KNOWN or value.name != BYTECODE_LOCAL_TYPE:
                    failures.append("%s: %s%s accessor operand %s is not a %s" % (
                        site.location(), prefix, info.name, base.normalize(site.java, argument[0], argument[-1] + 1),
                        BYTECODE_LOCAL_TYPE))
        return count, failures


def declaring_provenance(root, receiver, node):
    """Declaring-BytecodeNode provenance at a PE root reached with the given joint dimensions."""
    if receiver != RECEIVER_CONSTANT_OPERAND or node != NODE_BOUND:
        return DECLARING_UNPROVEN_OPERANDS
    if generated_root_of(root.owner) is None:
        return DECLARING_NOT_GENERATED_ROOT
    return None  # proven modulo the emission check, done by the caller with the operand position


# --------------------------------------------------------------------------
# PE reachability over every dimension, jointly per path
# --------------------------------------------------------------------------


def classify_reachability(graph, emissions, sink: AccessorSink):
    method = sink.site.method
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
    prove, classify = PROVERS[sink.family]
    receiver = operand._start_dimension(sink.receiver_class, sink._receiver_param, method)
    node = operand._start_dimension(sink.node_class, sink._node_param, method)
    first = ((method, receiver, node), False)
    parent = {first: None}
    queue = deque([first])
    terminals = {}
    roots = {}
    cuts = []
    unknown = []
    emission_failures = []

    def add_unknown(reason):
        graph.unknown_edges.add(reason.split("; reaches PE root", 1)[0])
        if reason not in unknown:
            unknown.append(reason)

    while queue:
        current_node = queue.popleft()
        (current, receiver, node), uncertain = current_node
        if base.is_pe_root(current):
            receiver_key = operand._root_dimension(current, receiver, prove, RECEIVER_CONSTANT_OPERAND)
            node_key = operand._root_dimension(current, node, operand.prove_bound_node, NODE_BOUND)
            declaring_key = NOT_APPLICABLE
            if sink.materialized:
                declaring_key = declaring_provenance(current, receiver_key, node_key)
                if declaring_key is None:
                    position = constant_operand_position(current.java, current, current.params[receiver[1]],
                                                         MATERIALIZED_ACCESSOR)
                    _, failures = emissions.check(current, position)
                    declaring_key = DECLARING_SAME_ROOT_GROUP if not failures else DECLARING_UNPROVEN_EMISSION
                    for failure in failures:
                        if failure not in emission_failures:
                            emission_failures.append(failure)
            key = (receiver_key, node_key, declaring_key)
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
                operand._argument_dimension(site, current, receiver, classify, RECEIVER_HELPER_PARAMETER,
                                            RECEIVER_CONSTANT_OPERAND),
                operand._argument_dimension(site, current, node, operand.classify_node, NODE_HELPER_PARAMETER,
                                            NODE_BOUND),
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

    def safe(key):
        return (key[0] == RECEIVER_CONSTANT_OPERAND and key[1] == NODE_BOUND
                and key[2] in (NOT_APPLICABLE, DECLARING_SAME_ROOT_GROUP))

    ordered = sorted(terminals, key=lambda key: (safe(key), key))
    sink.pe_paths = [
        {"receiver": key[0], "bytecode_node": key[1], "declaring_bytecode_node": key[2], "roots": len(roots[key]),
         "chain": terminals[key]}
        for key in ordered
    ]
    sink.pe_unknown = unknown
    sink.emission_failures = emission_failures
    if unknown:
        sink.pe_reachability = PE_REACHABILITY_UNKNOWN
    elif terminals:
        sink.receiver_pe = PROVEN if all(key[0] == RECEIVER_CONSTANT_OPERAND for key in terminals) else RISK
        sink.node_pe = PROVEN if all(key[1] == NODE_BOUND for key in terminals) else RISK
        if sink.materialized:
            sink.declaring_pe = PROVEN if all(key[2] == DECLARING_SAME_ROOT_GROUP for key in terminals) else RISK
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


def parse_sources(items):
    """Lexes and parses (label, source) pairs into base.ParsedFile objects.

    A file that mentions an accessor type but cannot be parsed is fatal; any
    other unparsable file is kept for the call graph's conservative handling.
    """
    parsed = []
    for label, source in items:
        tokens = base.lex(source, label)
        relevant = any(token.kind == "id" and token.text in ACCESSOR_TYPES for token in tokens)
        try:
            java = base.JavaFile(label, tokens)
            error = None
        except (GuardError, IndexError, KeyError, ValueError) as failure:
            if relevant:
                raise GuardError("%s: cannot parse a file that mentions %s: %s" % (
                    label, " or ".join(ACCESSOR_TYPES), failure))
            java = None
            error = str(failure)
        parsed.append(base.ParsedFile(label, tokens, java, error))
    return parsed


def analyze(parsed_files):
    graph = base.CallGraph(parsed_files)
    emissions = Emissions(graph)
    sinks = inventory(graph)
    for sink in sinks:
        classify_reachability(graph, emissions, sink)
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
        if entry["family"] not in ACCESSOR_TYPES:
            raise GuardError("baseline entry %d: family %r is not baselineable" % (position, entry["family"]))
        if entry["receiver_class"] not in RECEIVER_CLASSES or entry["bytecode_node_class"] not in NODE_CLASSES:
            raise GuardError("baseline entry %d: unknown local provenance class" % position)
        if entry["pe_reachability"] not in (PE_REACHABLE_PROVEN, BOUNDARY_CUT, NOT_PE_REACHABLE):
            raise GuardError("baseline entry %d: pe_reachability %r is not baselineable" % (
                position, entry["pe_reachability"]))
        if entry["pe_reachability"] == PE_REACHABLE_PROVEN and (
                entry["receiver_class"] not in (RECEIVER_CONSTANT_OPERAND, RECEIVER_HELPER_PARAMETER)
                or entry["bytecode_node_class"] not in (NODE_BOUND, NODE_HELPER_PARAMETER)):
            raise GuardError("baseline entry %d: a PE-reachable non-proven provenance is a risk and is never "
                             "baselineable" % position)
    return entries


def verify(sinks: List[AccessorSink], entries: List[dict]) -> List[str]:
    failures = []
    for sink in sinks:
        if sink.pe_reachability == PE_REACHABILITY_UNKNOWN:
            sink.status = STATUS_UNKNOWN
            failures.append("PE_REACHABILITY_UNKNOWN %s: %s" % (sink.label(), " | ".join(
                sink.pe_unknown[:5]) + (" | ..." if len(sink.pe_unknown) > 5 else "")))
        elif sink.pe_reachability == PE_REACHABLE_RISK:
            sink.status = STATUS_RISK
            chain = " -> ".join(base.short_key(each) for each in sink.pe_call_chain)
            for dimension, value in (("RECEIVER", sink.receiver_pe), ("BYTECODE_NODE", sink.node_pe),
                                     ("DECLARING_NODE", sink.declaring_pe)):
                if value == RISK:
                    failures.append("PE_REACHABLE_%s_%s_RISK %s via %s%s" % (
                        "MATERIALIZED" if sink.materialized else "LOCAL_ACCESSOR", dimension, sink.label(), chain,
                        "" if dimension != "DECLARING_NODE" or not sink.emission_failures else
                        " (builder emission: " + " | ".join(sink.emission_failures[:3]) + ")"))
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
    local = [sink for sink in sinks if sink.family == LOCAL_ACCESSOR]
    materialized = [sink for sink in sinks if sink.family == MATERIALIZED_ACCESSOR]

    def reachable(group):
        return [sink for sink in group if sink.pe_reachability in (PE_REACHABLE_PROVEN, PE_REACHABLE_RISK)]

    totals = {
        "TOTAL_LOCAL_ACCESSOR_SINKS": len(local),
        "TOTAL_MATERIALIZED_ACCESSOR_SINKS": len(materialized),
        "UNRESOLVED_RECEIVER_TYPE_SINKS": sum(1 for sink in sinks if sink.family == FAMILY_UNRESOLVED),
    }
    for name, group in (("LOCAL_ACCESSOR", local), ("MATERIALIZED", materialized)):
        dimensions = [("RECEIVER", "receiver_pe"), ("BYTECODE_NODE", "node_pe")]
        if name == "MATERIALIZED":
            dimensions.append(("DECLARING_NODE", "declaring_pe"))
        for label, attribute in dimensions:
            for value in (PROVEN, RISK):
                totals["PE_REACHABLE_%s_%s_%s" % (name, label, value)] = sum(
                    1 for sink in reachable(group) if getattr(sink, attribute) == value)
    for classification in PE_CLASSES:
        totals[classification] = sum(1 for sink in sinks if sink.pe_reachability == classification)
    totals["PE_ROOTS"] = len(graph.roots)
    totals["CALL_GRAPH_AMBIGUITIES"] = len(graph.unknown_edges)
    totals["UNPARSED_FILES"] = len(graph.unparsed)
    for status in (STATUS_NEW, STATUS_DRIFT):
        totals[status] = sum(1 for sink in sinks if sink.status == status)
    return totals


ACCEPTANCE_KEYS = (
    "PE_REACHABLE_LOCAL_ACCESSOR_RECEIVER_RISK",
    "PE_REACHABLE_LOCAL_ACCESSOR_BYTECODE_NODE_RISK",
    "PE_REACHABLE_MATERIALIZED_RECEIVER_RISK",
    "PE_REACHABLE_MATERIALIZED_BYTECODE_NODE_RISK",
    "PE_REACHABLE_MATERIALIZED_DECLARING_NODE_RISK",
    "PE_REACHABILITY_UNKNOWN",
)


def check(source: Path, baseline: Path, report: Optional[Path], label_root: Path, out=sys.stdout) -> int:
    try:
        parsed = scan_tree(source, label_root)
        entries = load_baseline(baseline)
    except GuardError as error:
        print("local-accessor-pe-guard: ANALYSIS_FAILED: %s" % error, file=out)
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
            "description": "Exact topology of every determinate PE-safe LocalAccessor/MaterializedLocalAccessor "
                           "sink. Risks are never baselined.",
            "entries": candidate,
        }, indent=2) + "\n", encoding="utf-8")
    print("local-accessor-pe-guard: %s receiver/BytecodeNode/declaring-node provenance" % "/".join(ACCESSOR_TYPES),
          file=out)
    print("  %-22s %-26s %-28s %-36s %s" % ("STATUS", "FAMILY", "RECEIVER", "BYTECODE_NODE", "PE / SINK"), file=out)
    for sink in sinks:
        pe = sink.pe_reachability
        if pe in (PE_REACHABLE_PROVEN, PE_REACHABLE_RISK):
            pe = "%s(r=%s,n=%s%s)" % (pe, sink.receiver_pe, sink.node_pe,
                                      ",d=%s" % sink.declaring_pe if sink.materialized else "")
        print("  %-22s %-26s %-28s %-36s %s %s:%d %s.%s %s.%s(%s, ...)" % (
            sink.status, sink.family, sink.receiver_class, sink.node_class, pe, sink.path.rsplit("/", 1)[-1],
            sink.line, sink.type_name.rsplit(".", 1)[-1], sink.method.split("(", 1)[0], sink.receiver,
            sink.operation, sink.bytecode_node), file=out)
    risks = [sink for sink in sinks if sink.status == STATUS_RISK]
    if risks:
        print("local-accessor-pe-guard: PE-reachable risks (every distinct provenance path)", file=out)
    for sink in risks:
        print("  %s" % sink.label(), file=out)
        print("    receiver: %s: %s" % (sink.receiver_class, sink.receiver_reason), file=out)
        print("    BytecodeNode: %s: %s" % (sink.node_class, sink.node_reason), file=out)
        for path in sink.pe_paths:
            print("    receiver=%s bytecode_node=%s declaring=%s roots=%d" % (
                path["receiver"], path["bytecode_node"], path["declaring_bytecode_node"], path["roots"]), file=out)
            chain = path["chain"]
            print("      " + base.short_key(chain[0]), file=out)
            for element in chain[1:]:
                print("      -> " + base.short_key(element), file=out)
    for key, value in totals.items():
        print("%s=%d" % (key, value), file=out)
    if failures:
        print("local-accessor-pe-guard: FAIL", file=out)
        for failure in failures:
            print("  " + failure, file=out)
        if candidate_path is not None:
            print("Candidate topology baseline (safe sites only; review before adopting): %s" % candidate_path,
                  file=out)
        return 1
    print("local-accessor-pe-guard: PASS", file=out)
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
