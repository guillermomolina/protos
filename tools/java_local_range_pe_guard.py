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


"""PERF030-F static LocalRangeAccessor partial-evaluation index guard.

Every indexed LocalRangeAccessor operation (isCleared/getObject/setObject/
clear and the typed get*/set* family) requires its index to be a
partial-evaluation constant. This guard inventories every such call in
production Java source and classifies the provenance of its index:

  PROVEN_CONSTANT_OPERAND  positional Bytecode DSL int @ConstantOperand
  DIRECT_CONSTANT          integer literal (or a never-reassigned literal local)
  TRUFFLE_BOUNDARY         enclosing method is @TruffleBoundary
  RUNTIME_NAME_DERIVED     local derived from offsetOf(...) (known risk)
  LOOP_INDEX               basic-for induction variable (risk, needs review)
  METHOD_PARAMETER         ordinary method parameter (risk, not proven)
  UNKNOWN                  anything not reliably classified (always a failure)

The first three are PROVEN_SAFE with respect to this guard. The risk classes
must each match one exact entry of the checked-in baseline; a new, changed,
or stale entry fails the guard. A passing guard never means that every
current site is partial-evaluation safe.

PERF030-G adds a second, independent dimension: conservative PE
reachability. Bytecode DSL operation specializations (@Specialization
methods of @Operation classes) are the PE roots; a call into a
@TruffleBoundary method cuts PE traversal. A conservative static call graph
over the scanned production source is searched backwards from every sink,
propagating index provenance across method parameters:

  PE_REACHABLE_PROVEN_CONSTANT  reached; every resolved path supplies a
                                structurally proven constant index
  PE_REACHABLE_RISK             reached by at least one path whose index is
                                not proven PE-constant
  BOUNDARY_CUT                  every otherwise relevant path is cut by
                                @TruffleBoundary
  NOT_PE_REACHABLE              no path from a known PE root
  PE_REACHABILITY_UNKNOWN       an unresolved call edge (unknown receiver,
                                ambiguous overload, unparsed file, ...) can
                                connect a PE root to the sink (always a failure)

Every determinate reachability result must match one exact entry of the
reachability baseline. Calls inside lambda and anonymous-class bodies are
attributed to the enclosing method (escaping functional objects are not
modelled), and only Bytecode DSL operations are roots: NOT_PE_REACHABLE does
not cover other Truffle entry points.

Subcommand:

  check --source DIR --baseline FILE [--reachability-baseline FILE] [--report FILE]

Standard library only; one process; no compilation, Maven, Graal, or network.
"""

from __future__ import print_function

import argparse
import json
import re
import sys
from collections import deque
from pathlib import Path
from typing import Dict, List, Optional, Tuple

ACCESSOR_TYPE = "LocalRangeAccessor"
INDEXED_ARITY = {
    "isCleared": 3,
    "clear": 3,
    "getObject": 3,
    "getBoolean": 3,
    "getByte": 3,
    "getInt": 3,
    "getLong": 3,
    "getFloat": 3,
    "getDouble": 3,
    "setObject": 4,
    "setBoolean": 4,
    "setByte": 4,
    "setInt": 4,
    "setLong": 4,
    "setFloat": 4,
    "setDouble": 4,
}
INDEX_ARGUMENT = 2

PROVEN_CONSTANT_OPERAND = "PROVEN_CONSTANT_OPERAND"
DIRECT_CONSTANT = "DIRECT_CONSTANT"
TRUFFLE_BOUNDARY = "TRUFFLE_BOUNDARY"
RUNTIME_NAME_DERIVED = "RUNTIME_NAME_DERIVED"
LOOP_INDEX = "LOOP_INDEX"
METHOD_PARAMETER = "METHOD_PARAMETER"
UNKNOWN = "UNKNOWN"
CLASSIFICATIONS = (
    PROVEN_CONSTANT_OPERAND,
    DIRECT_CONSTANT,
    TRUFFLE_BOUNDARY,
    RUNTIME_NAME_DERIVED,
    LOOP_INDEX,
    METHOD_PARAMETER,
    UNKNOWN,
)
SAFE_CLASSIFICATIONS = (PROVEN_CONSTANT_OPERAND, DIRECT_CONSTANT, TRUFFLE_BOUNDARY)
RISK_CLASSIFICATIONS = (RUNTIME_NAME_DERIVED, LOOP_INDEX, METHOD_PARAMETER)

STATUS_SAFE = "PROVEN_SAFE"
STATUS_BASELINED = "KNOWN_BASELINED_RISK"
STATUS_NEW = "NEW_UNBASELINED_RISK"
STATUS_UNKNOWN = "UNKNOWN"

BASELINE_SCHEMA = "protos-local-range-pe-guard-baseline-v1"
REPORT_SCHEMA = "protos-local-range-pe-guard-report-v2"
BASELINE_KEYS = ("path", "class", "method", "operation", "index", "classification", "occurrence")

PE_REACHABLE_PROVEN_CONSTANT = "PE_REACHABLE_PROVEN_CONSTANT"
PE_REACHABLE_RISK = "PE_REACHABLE_RISK"
BOUNDARY_CUT = "BOUNDARY_CUT"
NOT_PE_REACHABLE = "NOT_PE_REACHABLE"
PE_REACHABILITY_UNKNOWN = "PE_REACHABILITY_UNKNOWN"
PE_CLASSIFICATIONS = (
    PE_REACHABLE_PROVEN_CONSTANT,
    PE_REACHABLE_RISK,
    BOUNDARY_CUT,
    NOT_PE_REACHABLE,
    PE_REACHABILITY_UNKNOWN,
)

# Interprocedural index provenance at a PE root.
PROV_OPERATION_CONSTANT = "PE_CONSTANT_FROM_OPERATION"
PROV_DIRECT_CONSTANT = "DIRECT_CONSTANT"
PROV_RUNTIME_OPERAND = "OPERATION_RUNTIME_OPERAND"
PROV_UNPROVEN_ARGUMENT = "UNPROVEN_ARGUMENT"
PROVENANCES = (
    PROV_OPERATION_CONSTANT,
    PROV_DIRECT_CONSTANT,
    RUNTIME_NAME_DERIVED,
    LOOP_INDEX,
    PROV_RUNTIME_OPERAND,
    PROV_UNPROVEN_ARGUMENT,
)
CONSTANT_PROVENANCES = (PROV_OPERATION_CONSTANT, PROV_DIRECT_CONSTANT)
LOCAL_PROVENANCE = {
    PROVEN_CONSTANT_OPERAND: PROV_OPERATION_CONSTANT,
    DIRECT_CONSTANT: PROV_DIRECT_CONSTANT,
    RUNTIME_NAME_DERIVED: RUNTIME_NAME_DERIVED,
    LOOP_INDEX: LOOP_INDEX,
}
PROVENANCE_REASONS = {
    PROV_OPERATION_CONSTANT: "positional int @ConstantOperand of the root operation",
    PROV_DIRECT_CONSTANT: "integer literal or never-reassigned literal local",
    RUNTIME_NAME_DERIVED: "ordinal computed from a runtime name lookup (offsetOf)",
    LOOP_INDEX: "loop induction variable; not proven PE-unrolled",
    PROV_RUNTIME_OPERAND: "ordinary (non-@ConstantOperand) runtime operand of the root operation",
    PROV_UNPROVEN_ARGUMENT: "call argument whose constancy cannot be proven",
}

PE_STATUS_BASELINED = "BASELINED"
PE_STATUS_NEW = "NEW_UNBASELINED_REACHABILITY"
PE_STATUS_DRIFT = "REACHABILITY_DRIFT"
PE_STATUS_UNKNOWN = "UNKNOWN"
PE_STATUS_UNCHECKED = "NOT_CHECKED"

REACHABILITY_BASELINE_SCHEMA = "protos-local-range-pe-reachability-baseline-v1"
REACHABILITY_KEYS = BASELINE_KEYS + ("pe_reachability", "pe_provenance", "pe_call_chain")
REACHABILITY_CANDIDATE_NAME = "local-range-pe-reachability-baseline.candidate.json"
SEARCH_STATE_LIMIT = 200000

KEYWORDS = frozenset(
    "abstract assert boolean break byte case catch char class const continue default do double "
    "else enum extends final finally float for goto if implements import instanceof int interface "
    "long native new package private protected public return short static strictfp super switch "
    "synchronized this throw throws transient try void volatile while true false null var yield "
    "record sealed permits".split()
)
PRIMITIVES = frozenset("boolean byte char short int long float double void var".split())
NOT_A_TYPE_START = KEYWORDS - PRIMITIVES
MODIFIERS = frozenset(
    "public private protected static final abstract transient volatile synchronized native "
    "strictfp default sealed".split()
)
ASSIGNMENT_OPS = frozenset("= += -= *= /= %= &= |= ^= <<= >>= >>>=".split())
DECLARATION_PREDECESSORS = frozenset(["{", ";", "}", "(", ")", ":", "final"])
# Restricted identifiers that remain legal variable names.
CONTEXTUAL_NAMES = frozenset(["record", "sealed", "permits"])


class GuardError(Exception):
    pass


# --------------------------------------------------------------------------
# Lexing
# --------------------------------------------------------------------------

_OPERATORS = [
    ">>>=", "<<=", ">>=", "...", "->", "::", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
    "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<",
]
_TOKEN_RE = re.compile(
    r"(?P<ws>\s+)"
    r"|(?P<lc>//[^\n]*)"
    r"|(?P<bc>/\*.*?\*/)"
    r"|(?P<tb>\"\"\"[ \t\f]*\r?\n(?:[^\"\\]|\\.|\"(?!\"\"))*\"\"\")"
    r"|(?P<str>\"(?:[^\"\\\n]|\\.)*\")"
    r"|(?P<chr>'(?:[^'\\\n]|\\.)+')"
    r"|(?P<num>0[xX][0-9a-fA-F_]+[lL]?|0[bB][01_]+[lL]?"
    r"|\d[\d_]*(?:\.[\d_]*)?(?:[eE][+-]?\d+)?[fFdDlL]?|\.\d[\d_]*(?:[eE][+-]?\d+)?[fFdD]?)"
    r"|(?P<id>(?:[^\W\d]|\$)[\w$]*)"
    r"|(?P<op>" + "|".join(re.escape(op) for op in _OPERATORS) + r"|[{}()\[\];,.@=<>!~?:+\-*/&|^%])",
    re.DOTALL,
)


class Token(object):
    __slots__ = ("kind", "text", "line")

    def __init__(self, kind, text, line):
        self.kind = kind
        self.text = text
        self.line = line

    def __repr__(self):
        return "Token(%s,%r,%d)" % (self.kind, self.text, self.line)


def lex(source: str, label: str) -> List[Token]:
    """Tokenizes Java source, dropping comments and keeping exact lines.

    String/char/text-block literals are kept as opaque tokens so they can
    never be mistaken for identifiers. Unterminated or unrecognized input is
    a hard failure rather than a silent skip.
    """
    tokens = []
    line = 1
    position = 0
    length = len(source)
    while position < length:
        match = _TOKEN_RE.match(source, position)
        if match is None or match.end() == position:
            raise GuardError(
                "%s:%d: cannot tokenize near %r" % (label, line, source[position:position + 20])
            )
        kind = match.lastgroup
        text = match.group(kind)
        if kind == "op" and source.startswith("/*", position):
            raise GuardError("%s:%d: unterminated block comment" % (label, line))
        if kind == "str" and text == '""' and source.startswith('"""', position):
            raise GuardError("%s:%d: unterminated or malformed text block" % (label, line))
        if kind in ("str", "tb"):
            tokens.append(Token("str", text, line))
        elif kind == "chr":
            tokens.append(Token("chr", text, line))
        elif kind in ("num", "id", "op"):
            tokens.append(Token(kind, text, line))
        line += text.count("\n")
        position = match.end()
    return tokens


def match_brackets(tokens: List[Token], label: str) -> Tuple[Dict[int, int], List[int]]:
    """Returns the bracket match map and, per token, the innermost open bracket."""
    pairs = {")": "(", "]": "[", "}": "{"}
    matches = {}
    enclosing = []
    stack = []
    for index, token in enumerate(tokens):
        if token.kind == "op" and token.text in pairs:
            if not stack or tokens[stack[-1]].text != pairs[token.text]:
                raise GuardError("%s:%d: unbalanced %r" % (label, token.line, token.text))
            opener = stack.pop()
            matches[opener] = index
            matches[index] = opener
        enclosing.append(stack[-1] if stack else -1)
        if token.kind == "op" and token.text in ("(", "[", "{"):
            stack.append(index)
    if stack:
        raise GuardError("%s:%d: unclosed %r" % (label, tokens[stack[-1]].line, tokens[stack[-1]].text))
    return matches, enclosing


# --------------------------------------------------------------------------
# Structure
# --------------------------------------------------------------------------


T_KNOWN = "known"  # a value of the named static type
T_STATIC = "static"  # the named type itself (static member access)
T_SUPER = "super"  # 'super': ids holds the direct supertype names
T_EXTERNAL = "external"  # some type derived from an external API; ids bound its relevance


class JType(object):
    """Static type of a declaration or expression, as far as it is recoverable.

    'name' is the simple name ('[]' appended per array dimension), 'ids' every
    identifier of the written type, and 'args' its parsed top-level type
    arguments (None for an argument that could not be parsed). An expression
    whose type cannot be recovered is represented by None, never by a JType.
    """

    __slots__ = ("kind", "name", "ids", "args")

    def __init__(self, kind, name, ids=frozenset(), args=()):
        self.kind = kind
        self.name = name
        self.ids = frozenset(ids)
        self.args = args

    def __repr__(self):
        return "JType(%s,%s)" % (self.kind, self.name)


def _known(name):
    return JType(T_KNOWN, name, (name,))


class TypeInfo(object):
    def __init__(self, name, qualified, annotations, outer):
        self.name = name
        self.qualified = qualified
        self.annotations = annotations
        self.outer = outer
        self.fields = {}  # name -> Declaration
        self.kind = "class"
        self.supers = []  # simple names of extended/implemented types
        self.super_ids = set()  # every identifier of the extends/implements clauses
        self.type_params = {}  # name -> simple bound name or None
        self.methods = []  # concrete and abstract member methods
        self.java = None


class Declaration(object):
    def __init__(self, name, type_name, index, kind, init=None, scope_end=None, modifiers=()):
        self.name = name
        self.type_name = type_name
        self.index = index
        self.kind = kind  # "param" | "local" | "for" | "foreach" | "field"
        self.init = init  # (start, end) exclusive token range of the initializer
        self.scope_end = scope_end
        self.modifiers = frozenset(modifiers)
        self.jtype = None
        self.varargs = False


class Method(object):
    def __init__(self, owner, name, params, annotations, body_open, body_close):
        self.owner = owner
        self.name = name
        self.params = params
        self.annotations = annotations
        self.body_open = body_open
        self.body_close = body_close
        self.locals = []
        self.opaque = []  # lambda / anonymous-class token ranges
        self.name_index = None
        self.modifiers = frozenset()
        self.type_params = {}
        self.return_type = None
        self.has_return = False
        self.abstract = False
        self.arity_known = True
        self.java = None

    @property
    def signature(self):
        return "%s(%s)" % (self.name, ",".join(param.type_name for param in self.params))

    @property
    def varargs(self):
        return bool(self.params) and self.params[-1].varargs

    @property
    def is_constructor(self):
        return not self.has_return and self.owner is not None and self.name == self.owner.name


class JavaFile(object):
    def __init__(self, label: str, tokens: List[Token]):
        self.label = label
        self.tokens = tokens
        self.matches, self.enclosing = match_brackets(tokens, label)
        self.methods = []  # type: List[Method]
        # (start, end, owner, static) field initializers, enum constant bodies
        self.opaque_members = []
        self.types = []
        self.static_imports = []  # (type simple name, member name or '*')
        self._method_map = None
        self._parse_static_imports()
        self._parse_compilation_unit()
        for method in self.methods:
            self._collect_body(method)
        for info in self.types:
            info.java = self
            for method in info.methods:
                method.java = self

    # -- helpers ---------------------------------------------------------

    def text(self, index):
        return self.tokens[index].text if 0 <= index < len(self.tokens) else ""

    def is_op(self, index, text):
        return 0 <= index < len(self.tokens) and self.tokens[index].kind == "op" and self.tokens[index].text == text

    def is_id(self, index):
        return 0 <= index < len(self.tokens) and self.tokens[index].kind == "id"

    def fail(self, index, message):
        line = self.tokens[index].line if 0 <= index < len(self.tokens) else 0
        raise GuardError("%s:%d: %s" % (self.label, line, message))

    def strip_annotations(self, indices):
        """Splits a header index list into annotations and the remainder."""
        annotations = []
        rest = []
        position = 0
        while position < len(indices):
            index = indices[position]
            if self.is_op(index, "@") and self.text(index + 1) != "interface":
                cursor = index + 1
                if not self.is_id(cursor):
                    self.fail(index, "malformed annotation")
                while self.is_op(cursor + 1, ".") and self.is_id(cursor + 2):
                    cursor += 2
                name = self.text(cursor)
                end = cursor
                if self.is_op(cursor + 1, "("):
                    end = self.matches[cursor + 1]
                annotations.append((name, index, end))
                while position < len(indices) and indices[position] <= end:
                    position += 1
                continue
            rest.append(index)
            position += 1
        return annotations, rest

    def parse_type(self, indices, position):
        """Parses Type at indices[position]; returns (simple name, next position) or None."""
        if position >= len(indices) or not self.is_id(indices[position]):
            return None
        if self.text(indices[position]) in NOT_A_TYPE_START:
            return None
        simple = self.text(indices[position])
        position += 1
        while (
            position + 1 < len(indices)
            and self.is_op(indices[position], ".")
            and self.is_id(indices[position + 1])
        ):
            simple = self.text(indices[position + 1])
            position += 2
        if position < len(indices) and self.is_op(indices[position], "<"):
            depth = 0
            while position < len(indices):
                token = self.tokens[indices[position]]
                if token.kind == "op" and token.text == "<":
                    depth += 1
                elif token.kind == "op" and token.text == ">":
                    depth -= 1
                elif not (
                    token.kind == "id"
                    or (token.kind == "op" and token.text in (".", ",", "?", "&", "[", "]", "@"))
                ):
                    return None
                position += 1
                if depth == 0:
                    break
            if depth != 0:
                return None
        while (
            position + 1 < len(indices)
            and self.is_op(indices[position], "[")
            and self.is_op(indices[position + 1], "]")
        ):
            simple += "[]"
            position += 2
        if position < len(indices) and self.is_op(indices[position], "..."):
            simple += "[]"
            position += 1
        return simple, position

    def jtype_at(self, indices, position):
        """Like parse_type, but returns (JType, next position) keeping type arguments."""
        start = position
        if position >= len(indices) or not self.is_id(indices[position]):
            return None
        if self.text(indices[position]) in NOT_A_TYPE_START:
            return None
        simple = self.text(indices[position])
        position += 1
        while (
            position + 1 < len(indices)
            and self.is_op(indices[position], ".")
            and self.is_id(indices[position + 1])
        ):
            simple = self.text(indices[position + 1])
            position += 2
        args = ()
        if position < len(indices) and self.is_op(indices[position], "<"):
            depth = 0
            parts = []
            current = []
            while position < len(indices):
                index = indices[position]
                token = self.tokens[index]
                if token.kind == "op" and token.text == "<":
                    depth += 1
                    if depth > 1:
                        current.append(index)
                elif token.kind == "op" and token.text == ">":
                    depth -= 1
                    if depth >= 1:
                        current.append(index)
                elif token.kind == "op" and token.text == "," and depth == 1:
                    parts.append(current)
                    current = []
                elif token.kind == "id" or (token.kind == "op" and token.text in (".", ",", "?", "&", "[", "]", "@")):
                    current.append(index)
                else:
                    return None
                position += 1
                if depth == 0:
                    break
            if depth != 0:
                return None
            if current:
                parts.append(current)
            parsed_args = []
            for part in parts:
                if part and self.text(part[0]) == "?":
                    bound = None
                    if len(part) > 2 and self.text(part[1]) == "extends":
                        bound = self.jtype_at(part, 2)
                    parsed_args.append(bound[0] if bound is not None else (None if len(part) > 1 else _known("Object")))
                else:
                    parsed = self.jtype_at(part, 0)
                    parsed_args.append(parsed[0] if parsed is not None else None)
            args = tuple(parsed_args)
        while (
            position + 1 < len(indices)
            and self.is_op(indices[position], "[")
            and self.is_op(indices[position + 1], "]")
        ):
            simple += "[]"
            position += 2
        if position < len(indices) and self.is_op(indices[position], "..."):
            simple += "[]"
            position += 1
        ids = [self.text(indices[cursor]) for cursor in range(start, position) if self.is_id(indices[cursor])]
        return JType(T_KNOWN, simple, ids, args), position

    def skip_angles(self, index):
        """Returns the index after the '>' matching the '<' at index, or None."""
        depth = 0
        cursor = index
        while cursor < len(self.tokens):
            token = self.tokens[cursor]
            if token.kind == "op" and token.text == "<":
                depth += 1
            elif token.kind == "op" and token.text == ">":
                depth -= 1
                if depth == 0:
                    return cursor + 1
            elif not (token.kind == "id" or (token.kind == "op" and token.text in (".", ",", "?", "&", "[", "]", "@"))):
                return None
            cursor += 1
        return None

    def type_params_in(self, open_index, close_index):
        """Parses '<T extends B, U>' given the '<' and its matching '>' index."""
        params = {}
        for part in self.split_top_level(open_index + 1, close_index, angles=True):
            rest = [index for index in part if self.is_id(index)]
            if not rest:
                continue
            bound = None
            if len(rest) > 2 and self.text(rest[1]) == "extends":
                cursor = 2
                while cursor + 1 < len(rest) and rest[cursor + 1] == rest[cursor] + 2 and self.is_op(rest[cursor] + 1, "."):
                    cursor += 1
                bound = self.text(rest[cursor])
            params[self.text(rest[0])] = bound
        return params

    def _parse_static_imports(self):
        for index, token in enumerate(self.tokens):
            if token.kind != "id" or token.text != "import" or self.text(index + 1) != "static":
                continue
            cursor = index + 2
            parts = []
            while cursor < len(self.tokens) and not self.is_op(cursor, ";"):
                if self.is_id(cursor) or self.is_op(cursor, "*"):
                    parts.append(self.text(cursor))
                cursor += 1
            if len(parts) >= 2:
                self.static_imports.append((parts[-2], parts[-1]))

    def split_top_level(self, start, end, angles=False):
        """Splits the exclusive token range on commas outside brackets.

        With angles, '<'/'>' also nest (only valid where they are always
        generic brackets, such as a parameter list).
        """
        parts = []
        current = []
        angle_depth = 0
        index = start
        while index < end:
            token = self.tokens[index]
            if angles and token.kind == "op" and token.text in ("<", ">"):
                angle_depth += 1 if token.text == "<" else -1
                current.append(index)
                index += 1
                continue
            if token.kind == "op" and token.text in ("(", "[", "{"):
                close = self.matches[index]
                current.extend(range(index, close + 1))
                index = close + 1
                continue
            if token.kind == "op" and token.text == "," and angle_depth == 0:
                parts.append(current)
                current = []
            else:
                current.append(index)
            index += 1
        if current or parts:
            parts.append(current)
        return parts

    # -- members ---------------------------------------------------------

    def _parse_compilation_unit(self):
        self._parse_members(0, len(self.tokens), None, False)

    def _parse_members(self, start, end, owner, is_enum):
        header = []
        enum_constants_done = not is_enum
        index = start
        while index < end:
            token = self.tokens[index]
            if token.kind == "op" and token.text in ("(", "["):
                close = self.matches[index]
                header.extend(range(index, close + 1))
                index = close + 1
                continue
            if token.kind == "op" and token.text == ";":
                if enum_constants_done:
                    self._member_without_body(header, owner)
                enum_constants_done = True
                header = []
                index += 1
                continue
            if token.kind == "op" and token.text == "{":
                close = self.matches[index]
                if not enum_constants_done:
                    self.opaque_members.append((index, close, owner, True))
                    index = close + 1
                    continue
                consumed = self._member_with_body(header, index, close, owner)
                if consumed:
                    header = []
                else:
                    header.extend(range(index, close + 1))
                index = close + 1
                continue
            if token.kind == "op" and token.text == "}":
                self.fail(index, "unexpected '}' in member list")
            header.append(index)
            index += 1
        if owner is None:
            return
        if header and enum_constants_done:
            self.fail(header[0], "incomplete member declaration")

    def _type_keyword(self, rest):
        for position, index in enumerate(rest):
            text = self.text(index)
            if self.tokens[index].kind == "id" and text in ("class", "interface", "enum", "record"):
                if position > 0 and self.is_op(rest[position - 1], "."):
                    continue
                if text == "record" and not (position + 1 < len(rest) and self.is_id(rest[position + 1])):
                    continue
                return position, text
        return None

    def _has_top_level(self, rest, text):
        return any(self.is_op(index, text) for index in rest)

    def _member_with_body(self, header, open_index, close_index, owner):
        annotations, rest = self.strip_annotations(header)
        rest = [index for index in rest if not self.is_op(index, "@")]
        keyword = self._type_keyword(rest)
        if keyword is not None:
            position, kind = keyword
            if position + 1 >= len(rest) or not self.is_id(rest[position + 1]):
                self.fail(open_index, "type declaration without a name")
            name = self.text(rest[position + 1])
            qualified = name if owner is None else owner.qualified + "." + name
            info = TypeInfo(name, qualified, annotations, owner)
            info.kind = kind
            self._parse_type_header(info, rest[position + 1], open_index)
            self.types.append(info)
            if kind == "record" and position + 2 < len(rest) and self.is_op(rest[position + 2], "("):
                record_open = rest[position + 2]
                for param in self._parse_params(record_open):
                    param.kind = "field"
                    info.fields[param.name] = param
            self._parse_members(open_index + 1, close_index, info, kind == "enum")
            return True
        if owner is None:
            self.fail(open_index, "block outside of a type declaration")
        if self._has_top_level(rest, "="):
            # Field initializer containing a block (array initializer, lambda,
            # anonymous class): code inside is never classified as safe.
            static = any(self.text(index) == "static" for index in rest)
            self.opaque_members.append((open_index, close_index, owner, static))
            return False
        parenthesis = next((index for index in rest if self.is_op(index, "(")), None)
        if parenthesis is not None:
            name_index = parenthesis - 1
            if not self.is_id(name_index):
                self.fail(open_index, "cannot identify method name")
            method = Method(
                owner,
                self.text(name_index),
                self._parse_params(parenthesis),
                [annotation for annotation in annotations if annotation[1] < parenthesis],
                open_index,
                close_index,
            )
            self._parse_method_header(method, rest, name_index)
            self.methods.append(method)
            owner.methods.append(method)
            return True
        name = "<initializer>"
        if rest and all(self.text(index) in MODIFIERS for index in rest):
            name = "<static-initializer>" if any(self.text(index) == "static" for index in rest) else name
        elif rest:
            # Compact record constructor or an unrecognized shape.
            name = "<block:%s>" % " ".join(self.text(index) for index in rest)
        method = Method(owner, name, [], annotations, open_index, close_index)
        self.methods.append(method)
        owner.methods.append(method)
        return True

    def _parse_type_header(self, info, name_index, open_index):
        """Records type parameters and extends/implements names of a type header."""
        index = name_index + 1
        if self.is_op(index, "<"):
            after = self.skip_angles(index)
            if after is None:
                return
            info.type_params = self.type_params_in(index, after - 1)
            index = after
        if self.is_op(index, "("):
            index = self.matches[index] + 1
        collecting = False
        while index < open_index:
            token = self.tokens[index]
            if token.kind == "op" and token.text == "@":
                index += 2
                if self.is_op(index, "("):
                    index = self.matches[index] + 1
                continue
            if token.kind == "id" and token.text in ("extends", "implements"):
                collecting = True
                index += 1
                continue
            if token.kind == "id" and token.text == "permits":
                collecting = False
                index += 1
                continue
            if collecting and token.kind == "id":
                cursor = index
                while self.is_op(cursor + 1, ".") and self.is_id(cursor + 2):
                    cursor += 2
                info.supers.append(self.text(cursor))
                end = cursor + 1
                if self.is_op(end, "<"):
                    end = self.skip_angles(end) or end + 1
                for position in range(index, min(end, open_index)):
                    if self.is_id(position):
                        info.super_ids.add(self.text(position))
                index = end
                continue
            index += 1

    def _parse_method_header(self, method, rest, name_index):
        """Records modifiers, type parameters, and the return type of a method header."""
        method.name_index = name_index
        before = [index for index in rest if index < name_index]
        position = 0
        modifiers = set()
        while position < len(before):
            if self.text(before[position]) in MODIFIERS:
                modifiers.add(self.text(before[position]))
                position += 1
                continue
            if self.is_op(before[position], "<"):
                after = self.skip_angles(before[position])
                if after is None:
                    break
                method.type_params = self.type_params_in(before[position], after - 1)
                while position < len(before) and before[position] < after:
                    position += 1
                continue
            break
        method.modifiers = frozenset(modifiers)
        if position < len(before):
            method.has_return = True
            parsed = self.jtype_at(before, position)
            method.return_type = parsed[0] if parsed is not None else None

    def _member_without_body(self, header, owner):
        if owner is None or not header:
            return
        annotations, rest = self.strip_annotations(header)
        if self._type_keyword(rest) is not None:
            return
        modifiers = []
        position = 0
        while position < len(rest) and self.text(rest[position]) in MODIFIERS:
            modifiers.append(self.text(rest[position]))
            position += 1
        assignment = next((p for p in range(position, len(rest)) if self.is_op(rest[p], "=")), len(rest))
        parenthesis = next((rest[p] for p in range(position, assignment) if self.is_op(rest[p], "(")), None)
        if parenthesis is not None:
            # Abstract/interface method or annotation element: kept for name
            # lookup and return types only; it has no body.
            name_index = parenthesis - 1
            if self.is_id(name_index):
                try:
                    params = self._parse_params(parenthesis)
                    arity_known = True
                except GuardError:
                    params = []
                    arity_known = False
                method = Method(owner, self.text(name_index), params, annotations, -1, -1)
                method.abstract = True
                method.arity_known = arity_known
                self._parse_method_header(method, rest, name_index)
                owner.methods.append(method)
            return
        parsed = self.parse_type(rest, position)
        if parsed is None:
            return
        parsed_type = self.jtype_at(rest, position)
        type_name, position = parsed
        # Declarators: name [= init] {, name [= init]}
        while position < len(rest) and self.is_id(rest[position]):
            name_index = rest[position]
            position += 1
            while position + 1 < len(rest) and self.is_op(rest[position], "[") and self.is_op(rest[position + 1], "]"):
                position += 2
            init = None
            if position < len(rest) and self.is_op(rest[position], "="):
                init_start = position + 1
                while position < len(rest) and not self.is_op(rest[position], ","):
                    position += 1
                if init_start < position:
                    init = (rest[init_start], rest[position - 1] + 1)
                    self.opaque_members.append((init[0] - 1, init[1], owner, "static" in modifiers))
            field = Declaration(self.text(name_index), type_name, name_index, "field", init, None, modifiers)
            field.jtype = parsed_type[0] if parsed_type is not None else None
            owner.fields[self.text(name_index)] = field
            if position < len(rest) and self.is_op(rest[position], ","):
                position += 1
            else:
                break

    def _parse_params(self, open_index):
        close_index = self.matches[open_index]
        params = []
        for part in self.split_top_level(open_index + 1, close_index, angles=True):
            annotations, rest = self.strip_annotations(part)
            rest = [index for index in rest if self.text(index) != "final"]
            if not rest:
                self.fail(open_index, "empty parameter")
            parsed = self.parse_type(rest, 0)
            if parsed is None or parsed[1] != len(rest) - 1 or not self.is_id(rest[-1]):
                self.fail(open_index, "unsupported parameter syntax")
            declaration = Declaration(self.text(rest[-1]), parsed[0], rest[-1], "param")
            declaration.annotations = annotations
            parsed_type = self.jtype_at(rest, 0)
            declaration.jtype = parsed_type[0] if parsed_type is not None else None
            declaration.varargs = any(self.is_op(index, "...") for index in rest)
            params.append(declaration)
        return params

    # -- method bodies ---------------------------------------------------

    def _statement_end(self, start, limit):
        """First ';', or close of the enclosing bracket, at the depth of start."""
        index = start
        while index < limit:
            token = self.tokens[index]
            if token.kind == "op" and token.text in ("(", "[", "{"):
                index = self.matches[index] + 1
                continue
            if token.kind == "op" and token.text in (";", ",", ")", "]", "}"):
                return index
            index += 1
        return limit

    def _is_switch_rule_arrow(self, arrow):
        parent = self.enclosing[arrow]
        if parent < 0 or not self.is_op(parent, "{") or not self.is_op(parent - 1, ")"):
            return False
        return self.text(self.matches[parent - 1] - 1) == "switch"

    def _is_anonymous_class_body(self, brace):
        if not self.is_op(brace - 1, ")"):
            return False
        cursor = self.matches[brace - 1] - 1
        if self.is_op(cursor, ">"):
            depth = 0
            while cursor > 0:
                if self.is_op(cursor, ">"):
                    depth += 1
                elif self.is_op(cursor, "<"):
                    depth -= 1
                cursor -= 1
                if depth == 0:
                    break
        if not self.is_id(cursor):
            return False
        while self.is_op(cursor - 1, ".") and self.is_id(cursor - 2):
            cursor -= 2
        return self.text(cursor - 1) == "new"

    def _collect_body(self, method):
        start = method.body_open + 1
        end = method.body_close
        for index in range(start, end):
            token = self.tokens[index]
            if token.kind == "op" and token.text == "->" and not self._is_switch_rule_arrow(index):
                if self.is_op(index + 1, "{"):
                    method.opaque.append((index, self.matches[index + 1]))
                else:
                    method.opaque.append((index, self._statement_end(index + 1, end)))
            elif token.kind == "op" and token.text == "{" and self._is_anonymous_class_body(index):
                method.opaque.append((index, self.matches[index]))
            elif (
                token.kind == "id"
                and token.text in ("class", "interface", "enum", "record")
                and not self.is_op(index - 1, ".")
                and self.is_id(index + 1)
            ):
                # Local type declaration: its members are not this method's code.
                brace = index + 2
                while brace < end and not self.is_op(brace, "{") and not self.is_op(brace, ";"):
                    brace = self.matches[brace] + 1 if self.is_op(brace, "(") else brace + 1
                if self.is_op(brace, "{"):
                    method.opaque.append((index, self.matches[brace]))
            elif token.kind == "id" and token.text in ("instanceof", "case"):
                declaration = self._try_pattern_binding(index, start, end)
                if declaration is not None:
                    method.locals.append(declaration)
            elif token.kind == "id" and self.text(index - 1) in DECLARATION_PREDECESSORS:
                declaration = self._try_local_declaration(index, end)
                if declaration is not None:
                    method.locals.append(declaration)

    def _try_local_declaration(self, index, limit):
        if self.tokens[index - 1].kind not in ("op", "id"):
            return None
        if self.tokens[index - 1].kind == "id" and self.text(index - 1) != "final":
            return None
        cursor_list = list(range(index, min(limit, index + 64)))
        parsed = self.parse_type(cursor_list, 0)
        if parsed is None:
            return None
        type_name, position = parsed
        if position >= len(cursor_list) or not self.is_id(cursor_list[position]):
            return None
        name_index = cursor_list[position]
        if self.text(name_index) in KEYWORDS and self.text(name_index) not in CONTEXTUAL_NAMES:
            return None
        follower = name_index + 1
        if not (self.tokens[follower].kind == "op" and self.tokens[follower].text in ("=", ";", ":", ",", ")", "[")):
            return None
        parent = self.enclosing[index]
        kind = "local"
        if parent >= 0 and self.is_op(parent, "(") and self.text(parent - 1) == "for":
            kind = "foreach" if self.is_op(follower, ":") else "for"
        init = None
        if self.is_op(follower, "="):
            init = (follower + 1, self._statement_end(follower + 1, limit))
        elif kind == "foreach":
            init = (follower + 1, self.matches[parent])  # the iterated expression
        if parent >= 0 and self.is_op(parent, "("):
            after = self.matches[parent] + 1
            if self.is_op(after, "{"):
                scope_end = self.matches[after]
            else:
                scope_end = self._statement_end(after, limit)
        elif parent >= 0:
            scope_end = self.matches[parent]
        else:
            scope_end = limit
        declaration = Declaration(self.text(name_index), type_name, name_index, kind, init, scope_end)
        parsed_type = self.jtype_at(cursor_list, 0)
        declaration.jtype = parsed_type[0] if parsed_type is not None else None
        return declaration

    def _try_pattern_binding(self, keyword, body_start, limit):
        """Declares the binding of 'x instanceof Type name' or 'case Type name'.

        The binding is visible until the end of the innermost enclosing block,
        which covers both 'if (x instanceof T t && ...) {...}' and the
        flow-scoped 'if (!(x instanceof T t)) { throw ...; } use(t);' shapes.
        """
        cursor_list = list(range(keyword + 1, min(limit, keyword + 64)))
        parsed = self.parse_type(cursor_list, 0)
        if parsed is None or parsed[1] >= len(cursor_list):
            return None
        name_index = cursor_list[parsed[1]]
        if not self.is_id(name_index):
            return None
        if self.text(name_index) in KEYWORDS and self.text(name_index) not in CONTEXTUAL_NAMES:
            return None
        follower = self.tokens[name_index + 1]
        if follower.kind == "op" and follower.text in ("(", ".", "=", "<", "["):
            return None
        block = self.enclosing[keyword]
        while block >= body_start and not self.is_op(block, "{"):
            block = self.enclosing[block]
        scope_end = self.matches[block] if block >= body_start else limit
        declaration = Declaration(self.text(name_index), parsed[0], name_index, "local", None, scope_end)
        parsed_type = self.jtype_at(cursor_list, 0)
        declaration.jtype = parsed_type[0] if parsed_type is not None else None
        return declaration

    # -- lookup ----------------------------------------------------------

    def method_at(self, index) -> Optional[Method]:
        if self._method_map is None:
            self._method_map = [None] * len(self.tokens)
            for method in sorted(self.methods, key=lambda each: each.body_open):
                for position in range(method.body_open + 1, method.body_close):
                    self._method_map[position] = method
        return self._method_map[index] if 0 <= index < len(self.tokens) else None

    def opaque_member_entry_at(self, index):
        for entry in self.opaque_members:
            if entry[0] < index < entry[1]:
                return entry
        return None

    def opaque_member_at(self, index):
        entry = self.opaque_member_entry_at(index)
        return entry[2] if entry is not None else None

    def lookup(self, method: Optional[Method], name: str, index: int, fields_only=False, owner=None):
        if method is not None and not fields_only:
            visible = [
                local
                for local in method.locals
                if local.name == name and local.index < index <= local.scope_end
            ]
            if visible:
                return max(visible, key=lambda local: local.index)
            for param in method.params:
                if param.name == name:
                    return param
        info = method.owner if method is not None else owner
        while info is not None:
            if name in info.fields:
                return info.fields[name]
            if fields_only:
                return None
            info = info.outer
        return None


# --------------------------------------------------------------------------
# Sink discovery and classification
# --------------------------------------------------------------------------


class Sink(object):
    def __init__(self, path, line, column_index, type_name, method, receiver, operation, index_text):
        self.path = path
        self.line = line
        self.column_index = column_index
        self.type_name = type_name
        self.method = method
        self.receiver = receiver
        self.operation = operation
        self.index = index_text
        self.classification = UNKNOWN
        self.reason = ""
        self.occurrence = 0
        self.status = STATUS_UNKNOWN
        self._java = None
        self._method = None
        self._param = None
        self.pe_reachability = None
        self.pe_provenance = []
        self.pe_paths = []
        self.pe_call_chain = []
        self.pe_boundary_paths = []
        self.pe_unknown = []
        self.pe_status = PE_STATUS_UNCHECKED

    def key(self):
        return (self.path, self.type_name, self.method, self.operation, self.index, self.classification, self.occurrence)

    def to_json(self):
        return {
            "path": self.path,
            "line": self.line,
            "class": self.type_name,
            "method": self.method,
            "receiver": self.receiver,
            "operation": self.operation,
            "index": self.index,
            "classification": self.classification,
            "occurrence": self.occurrence,
            "reason": self.reason,
            "status": self.status,
            "pe_reachability": self.pe_reachability,
            "pe_provenance": list(self.pe_provenance),
            "pe_call_chain": list(self.pe_call_chain),
            "pe_paths": [dict(path) for path in self.pe_paths],
            "pe_boundary_paths": [list(path) for path in self.pe_boundary_paths],
            "pe_unknown": list(self.pe_unknown),
            "pe_status": self.pe_status,
        }


def normalize(java: JavaFile, start: int, end: int) -> str:
    out = []
    previous = None
    for index in range(start, end):
        token = java.tokens[index]
        if previous is not None and previous.kind in ("id", "num") and token.kind in ("id", "num"):
            out.append(" ")
        out.append(token.text)
        previous = token
    return "".join(out)


def _receiver(java: JavaFile, dot: int) -> Tuple[str, Optional[int], bool]:
    """Returns (receiver text, identifier index or None, fields_only)."""
    before = dot - 1
    if java.is_id(before):
        if java.is_op(before - 1, ".") or java.is_op(before - 1, "::"):
            if java.text(before - 2) == "this" and not java.is_op(before - 3, "."):
                return "this." + java.text(before), before, True
            start = before
            while java.is_op(start - 1, ".") and (java.is_id(start - 2) or java.is_op(start - 2, ")")):
                start -= 2
                if java.is_op(start, ")"):
                    start = java.matches[start] - 1
            return normalize(java, max(start, 0), dot), None, False
        return java.text(before), before, False
    if java.is_op(before, ")") or java.is_op(before, "]"):
        opener = java.matches[before]
        return normalize(java, max(opener - 1, 0), dot), None, False
    return java.text(before), None, False


def _receiver_kind(java: JavaFile, method, owner, receiver_index, fields_only, dot) -> str:
    """'accessor', 'other', or 'unknown'."""
    if receiver_index is None:
        return "unknown"
    name = java.text(receiver_index)
    if name in ("this", "super"):
        return "other"
    declaration = java.lookup(method, name, dot, fields_only, owner)
    if declaration is None:
        if name == ACCESSOR_TYPE:
            return "unknown"
        if name[:1].isupper():
            return "other"  # static member of a type
        return "unknown"
    if declaration.type_name == ACCESSOR_TYPE:
        return "accessor"
    if declaration.type_name == "var":
        return "unknown"
    return "other"


def _reassigned(java: JavaFile, name: str, start: int, end: int, skip=()):
    for index in range(start, end):
        if index in skip or not java.is_id(index) or java.text(index) != name:
            continue
        if java.is_op(index - 1, ".") or java.is_op(index - 1, "::"):
            continue
        following = java.tokens[index + 1] if index + 1 < len(java.tokens) else None
        if following is not None and following.kind == "op" and following.text in ASSIGNMENT_OPS:
            return index
        if following is not None and following.kind == "op" and following.text in ("++", "--"):
            return index
        if java.is_op(index - 1, "++") or java.is_op(index - 1, "--"):
            return index
    return None


def _is_int_literal(token: Token) -> bool:
    return token.kind == "num" and re.match(r"^(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*)[lL]?$", token.text) is not None


def _annotation_names(annotations):
    return set(name for name, _, _ in annotations)


def _constant_operands(java: JavaFile, info: TypeInfo):
    """Parses the positional @ConstantOperand list, or None if not provable."""
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
            values[java.text(part[0])] = normalize(java, part[2], part[-1] + 1)
        if "specifyAtEnd" in values or "type" not in values or "name" not in values:
            return None
        operands.append((values["type"], values["name"].strip('"')))
    return operands


def _prove_constant_operand(java: JavaFile, method: Method, param) -> Optional[str]:
    if "Specialization" not in _annotation_names(method.annotations):
        return None
    if "Operation" not in _annotation_names(method.owner.annotations):
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
    if operand_type != "int.class" or param.type_name != "int" or operand_name != param.name:
        return None
    return "positional @ConstantOperand #%d (type = int.class, name = \"%s\") of @Operation %s" % (
        position,
        operand_name,
        method.owner.qualified,
    )


def _classify_identifier(java: JavaFile, method: Method, name: str, at: int, seen, found=None) -> Tuple[str, str]:
    """Classifies an index identifier; appends a terminal parameter to 'found'."""
    if name in seen or len(seen) > 8:
        return UNKNOWN, "cyclic or too deep local alias chain at '%s'" % name
    seen = seen | {name}
    declaration = java.lookup(method, name, at)
    if declaration is None:
        return UNKNOWN, "'%s' does not resolve to a local, parameter, or field" % name
    if declaration.kind == "param":
        written = _reassigned(java, name, method.body_open, method.body_close)
        if written is not None:
            return UNKNOWN, "parameter '%s' is reassigned at line %d" % (name, java.tokens[written].line)
        if found is not None:
            found.append(declaration)
        proof = _prove_constant_operand(java, method, declaration)
        if proof is not None:
            return PROVEN_CONSTANT_OPERAND, "parameter '%s' is the %s" % (name, proof)
        return METHOD_PARAMETER, "ordinary %s parameter '%s'; constancy not provable" % (declaration.type_name, name)
    if declaration.kind == "field":
        if (
            "static" in declaration.modifiers
            and "final" in declaration.modifiers
            and declaration.init is not None
            and declaration.init[1] - declaration.init[0] == 1
            and _is_int_literal(java.tokens[declaration.init[0]])
        ):
            return DIRECT_CONSTANT, "static final field '%s' = %s" % (name, java.text(declaration.init[0]))
        return UNKNOWN, "index read from field '%s'" % name
    if declaration.kind == "foreach":
        return UNKNOWN, "enhanced-for element '%s'" % name
    if declaration.kind == "for":
        header_open = java.enclosing[declaration.index]
        header_close = java.matches[header_open]
        written = _reassigned(java, name, header_close + 1, declaration.scope_end + 1)
        if written is not None:
            return UNKNOWN, "loop variable '%s' is written in the loop body at line %d" % (
                name,
                java.tokens[written].line,
            )
        return LOOP_INDEX, "induction variable of for (%s)" % normalize(java, header_open + 1, header_close)
    written = _reassigned(java, name, declaration.index + 1, declaration.scope_end + 1, skip={declaration.index})
    if written is not None:
        return UNKNOWN, "local '%s' is reassigned at line %d" % (name, java.tokens[written].line)
    if declaration.init is None:
        return UNKNOWN, "local '%s' has no initializer" % name
    start, end = declaration.init
    init_text = normalize(java, start, end)
    for index in range(start, end):
        if java.text(index) == "offsetOf" and java.is_op(index - 1, ".") and java.is_op(index + 1, "("):
            return RUNTIME_NAME_DERIVED, "local '%s' = %s (runtime name lookup)" % (name, init_text)
    if end - start == 1 and _is_int_literal(java.tokens[start]):
        return DIRECT_CONSTANT, "never-reassigned local '%s' = %s" % (name, init_text)
    if end - start == 1 and java.is_id(start) and java.text(start) not in KEYWORDS:
        classification, reason = _classify_identifier(java, method, java.text(start), declaration.index, seen, found)
        return classification, "local alias '%s' = %s; %s" % (name, init_text, reason)
    return UNKNOWN, "local '%s' = %s (unrecognized provenance)" % (name, init_text)


def _classify_index(java: JavaFile, method: Method, start: int, end: int, at: int, found=None) -> Tuple[str, str]:
    count = end - start
    if count == 1 and _is_int_literal(java.tokens[start]):
        return DIRECT_CONSTANT, "integer literal"
    if count == 1 and java.is_id(start) and java.text(start) not in KEYWORDS:
        return _classify_identifier(java, method, java.text(start), at, frozenset(), found)
    if count == 3 and java.text(start) == "this" and java.is_op(start + 1, ".") and java.is_id(start + 2):
        return UNKNOWN, "index read from field 'this.%s'" % java.text(start + 2)
    return UNKNOWN, "compound index expression"


def _sink_candidates(tokens: List[Token]) -> List[int]:
    candidates = []
    for index, token in enumerate(tokens):
        if token.kind != "id" or token.text not in INDEXED_ARITY:
            continue
        if index == 0 or tokens[index - 1].kind != "op" or tokens[index - 1].text not in (".", "::"):
            continue
        candidates.append(index)
    return candidates


def analyze_file(path_label: str, source: str) -> List[Sink]:
    tokens = lex(source, path_label)
    candidates = _sink_candidates(tokens)
    if not candidates:
        return []
    return _analyze_parsed(path_label, JavaFile(path_label, tokens), candidates)


def _analyze_parsed(path_label: str, java: JavaFile, candidates: List[int]) -> List[Sink]:
    tokens = java.tokens
    sinks = []
    for index in candidates:
        operation = java.text(index)
        dot = index - 1
        method = java.method_at(index)
        owner = method.owner if method is not None else java.opaque_member_at(index)
        receiver_text, receiver_index, fields_only = _receiver(java, dot)
        reference = java.is_op(dot, "::")
        arguments = None
        if not reference:
            if not java.is_op(index + 1, "("):
                continue
            close = java.matches[index + 1]
            arguments = java.split_top_level(index + 2, close)
            if len(arguments) != INDEXED_ARITY[operation]:
                continue
        kind = _receiver_kind(java, method, owner, receiver_index, fields_only, dot)
        if kind == "other":
            continue
        type_name = owner.qualified if owner is not None else "<unknown>"
        method_name = method.signature if method is not None else "<outside-method>"
        index_text = "<method-reference>"
        if arguments is not None:
            index_tokens = arguments[INDEX_ARGUMENT]
            if not index_tokens:
                java.fail(index, "empty index argument")
            index_text = normalize(java, index_tokens[0], index_tokens[-1] + 1)
        sink = Sink(path_label, tokens[index].line, index, type_name, method_name, receiver_text, operation, index_text)
        sink._java = java
        sink._method = method
        sinks.append(sink)
        if kind == "unknown":
            sink.reason = "receiver '%s' is not statically identifiable as %s" % (receiver_text, ACCESSOR_TYPE)
            continue
        if reference:
            sink.reason = "method reference to an indexed %s operation" % ACCESSOR_TYPE
            continue
        if method is None:
            sink.reason = "indexed access outside a method body (field initializer or enum constant)"
            continue
        if any(start < index <= end for start, end in method.opaque):
            sink.reason = "indexed access inside a lambda or anonymous class body"
            continue
        index_tokens = arguments[INDEX_ARGUMENT]
        found = []
        classification, reason = _classify_index(java, method, index_tokens[0], index_tokens[-1] + 1, index, found)
        sink._param = found[-1] if found else None
        if "TruffleBoundary" in _annotation_names(method.annotations):
            sink.classification = TRUFFLE_BOUNDARY
            sink.reason = "enclosing method is @TruffleBoundary (index provenance: %s: %s)" % (classification, reason)
        else:
            sink.classification = classification
            sink.reason = reason
    occurrences = {}
    for sink in sinks:
        identity = sink.key()[:-1]
        sink.occurrence = occurrences.get(identity, 0)
        occurrences[identity] = sink.occurrence + 1
    return sinks


class ParsedFile(object):
    """One scanned source file; java is None when its structure could not be parsed."""

    def __init__(self, label, tokens, java, error=None):
        self.label = label
        self.tokens = tokens
        self.java = java
        self.error = error
        self.call_names = None
        if java is None:
            # Conservative textual call-name set used to fail closed.
            names = set()
            for index, token in enumerate(tokens):
                if token.kind != "id":
                    continue
                following = tokens[index + 1] if index + 1 < len(tokens) else None
                previous = tokens[index - 1] if index > 0 else None
                if (following is not None and following.kind == "op" and following.text == "(") or (
                    previous is not None and previous.kind in ("op", "id") and previous.text in ("::", "new")
                ):
                    names.add(token.text)
            self.call_names = names


def parse_sources(items) -> Tuple[List[Sink], List[ParsedFile]]:
    """Lexes and parses (label, source) pairs; returns (sorted sinks, parsed files).

    A lexing failure, or a parse failure in a file that contains sink
    candidates, is fatal. Any other file that cannot be parsed is kept as an
    unparsed file, which the reachability analysis treats conservatively.
    """
    sinks = []
    parsed = []
    for label, source in items:
        tokens = lex(source, label)
        candidates = _sink_candidates(tokens)
        try:
            java = JavaFile(label, tokens)
            error = None
        except (GuardError, IndexError, KeyError, ValueError) as failure:
            if candidates:
                raise
            java = None
            error = str(failure)
        if candidates:
            sinks.extend(_analyze_parsed(label, java, candidates))
        parsed.append(ParsedFile(label, tokens, java, error))
    sinks.sort(key=lambda sink: (sink.path, sink.line, sink.column_index))
    return sinks, parsed


def scan_tree(source_root: Path, label_root: Path) -> Tuple[List[Sink], List[ParsedFile]]:
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


def scan(source_root: Path, label_root: Path) -> List[Sink]:
    return scan_tree(source_root, label_root)[0]


# --------------------------------------------------------------------------
# PERF030-G: conservative call graph and PE reachability
# --------------------------------------------------------------------------

EDGE_RESOLVED = "resolved"
EDGE_UNCERTAIN = "uncertain"
EDGE_IMMEDIATE = "immediate"  # the calling context itself cannot be determined

PRIMITIVE_TYPES = frozenset("boolean byte char short int long float double".split())
BOXES = {
    "boolean": "Boolean", "byte": "Byte", "char": "Character", "short": "Short",
    "int": "Integer", "long": "Long", "float": "Float", "double": "Double",
}
UNBOXES = dict((box, primitive) for primitive, box in BOXES.items())
WIDENING = {
    "byte": ("short", "int", "long", "float", "double"),
    "short": ("int", "long", "float", "double"),
    "char": ("int", "long", "float", "double"),
    "int": ("long", "float", "double"),
    "long": ("float", "double"),
    "float": ("double",),
}
STRING_SUPERS = frozenset(["String", "Object", "CharSequence", "Comparable", "Serializable", "Constable", "ConstantDesc"])
# Element-returning methods of external generic containers: the result is the
# container's type argument rather than an arbitrary external type.
MAP_TYPES = frozenset(
    "Map HashMap LinkedHashMap TreeMap SortedMap NavigableMap ConcurrentMap ConcurrentHashMap "
    "IdentityHashMap WeakHashMap EnumMap EconomicMap".split()
)
MAP_VALUE_METHODS = frozenset(
    "get getOrDefault put remove putIfAbsent computeIfAbsent computeIfPresent compute merge replace".split()
)
ELEMENT_METHODS = frozenset(
    "get getFirst getLast remove removeFirst removeLast poll pollFirst pollLast peek peekFirst peekLast "
    "pop element next previous orElse orElseGet orElseThrow".split()
)


def method_key(method: Method) -> str:
    return "%s.%s" % (method.owner.qualified, method.signature)


def short_key(label: str) -> str:
    head, separator, tail = label.partition("(")
    if not separator:
        return label
    suffix = tail.partition(")")[2]
    return head + suffix


def is_pe_root(method: Method) -> bool:
    return (
        not method.abstract
        and method.body_open >= 0
        and "Specialization" in _annotation_names(method.annotations)
        and method.owner is not None
        and "Operation" in _annotation_names(method.owner.annotations)
    )


def is_truffle_boundary(method: Method) -> bool:
    return "TruffleBoundary" in _annotation_names(method.annotations)


def _chain_start(java: JavaFile, end: int) -> int:
    """First token of the primary/postfix expression chain ending at end (inclusive)."""
    index = end
    while index >= 0:
        if java.is_op(index, "]"):
            index = java.matches[index] - 1
            continue
        if java.is_op(index, ")"):
            opener = java.matches[index]
            start = opener
            if java.is_id(opener - 1) and java.text(opener - 1) not in KEYWORDS:
                start = opener - 1
                cursor = start
                while java.is_op(cursor - 1, ".") and java.is_id(cursor - 2) and java.text(cursor - 2) not in KEYWORDS:
                    cursor -= 2
                if java.text(cursor - 1) == "new":
                    return cursor - 1
            elif java.is_op(opener - 1, ">"):
                cursor = opener - 1
                depth = 0
                while cursor > 0:
                    if java.is_op(cursor, ">"):
                        depth += 1
                    elif java.is_op(cursor, "<"):
                        depth -= 1
                        if depth == 0:
                            break
                    cursor -= 1
                cursor -= 1
                while java.is_op(cursor - 1, ".") and java.is_id(cursor - 2):
                    cursor -= 2
                if java.is_id(cursor) and java.text(cursor - 1) == "new":
                    return cursor - 1
        elif java.tokens[index].kind == "str" or (
            java.is_id(index)
            and (java.text(index) not in KEYWORDS or java.text(index) in ("this", "super") or java.text(index) in CONTEXTUAL_NAMES)
        ):
            start = index
        else:
            return index + 1
        if java.is_op(start - 1, "."):
            index = start - 2
            continue
        return start
    return 0


class CallSite(object):
    __slots__ = ("java", "index", "name", "kind", "dot", "args", "method", "owner", "caller", "opaque", "line",
                 "receiver_done", "receiver", "arg_types")

    def __init__(self, java, index, name, kind, dot, args, method, owner, caller, opaque):
        self.java = java
        self.index = index
        self.name = name
        self.kind = kind  # "call" | "new" | "this" | "super" | "ref"
        self.dot = dot  # '.' or '::' token index of a qualified call, else None
        self.args = args  # list of token-index lists, or None for a method reference
        self.method = method  # enclosing real method (typing context), or None
        self.owner = owner
        self.caller = caller  # graph node: real method or field-initializer pseudo method
        self.opaque = opaque  # inside a lambda / anonymous-class / local-class body
        self.line = java.tokens[index].line
        self.receiver_done = False
        self.receiver = None
        self.arg_types = None

    def location(self):
        return "%s:%d" % (self.java.label, self.line)


class CallGraph(object):
    """Conservative static call graph over parsed production source."""

    def __init__(self, parsed_files: List[ParsedFile]):
        self.files = [parsed for parsed in parsed_files if parsed.java is not None]
        self.unparsed = [parsed for parsed in parsed_files if parsed.java is None]
        self.types_by_name = {}
        self.sub_index = {}
        self.methods_by_name = {}
        self.sites_by_name = {}
        self.new_sites = {}
        self.constructor_sites = []
        self.roots = []
        self.method_count = 0
        self.site_count = 0
        self.unknown_edges = set()
        self._supers = {}
        self._compat = {}
        self._declares = {}
        self._callers = {}
        self._pseudo = {}
        for parsed in self.files:
            for info in parsed.java.types:
                self.types_by_name.setdefault(info.name, []).append(info)
                for name in info.supers:
                    self.sub_index.setdefault(name, set()).add(info.name)
                for method in info.methods:
                    self.methods_by_name.setdefault(method.name, []).append(method)
                    if not method.abstract:
                        self.method_count += 1
                        if is_pe_root(method):
                            self.roots.append(method)
        for parsed in self.files:
            self._collect_sites(parsed.java)

    # -- sites -----------------------------------------------------------

    def _pseudo_method(self, owner, static):
        key = (id(owner), static)
        if key not in self._pseudo:
            pseudo = Method(owner, "<static-field-init>" if static else "<field-init>", [], [], -1, -1)
            pseudo.java = owner.java
            self._pseudo[key] = pseudo
        return self._pseudo[key]

    def _context(self, java, index):
        method = java.method_at(index)
        if method is not None:
            opaque = any(start < index <= end for start, end in method.opaque)
            return method, method.owner, method, opaque
        entry = java.opaque_member_entry_at(index)
        if entry is None or entry[2] is None:
            return None
        return None, entry[2], self._pseudo_method(entry[2], entry[3]), False

    def _add_site(self, java, index, name, kind, dot, args):
        context = self._context(java, index)
        if context is None:
            return
        method, owner, caller, opaque = context
        site = CallSite(java, index, name, kind, dot, args, method, owner, caller, opaque)
        self.site_count += 1
        if kind == "new":
            self.new_sites.setdefault(name, []).append(site)
        elif kind in ("this", "super"):
            self.constructor_sites.append(site)
        else:
            self.sites_by_name.setdefault(name, []).append(site)

    def _collect_sites(self, java):
        tokens = java.tokens
        for index, token in enumerate(tokens):
            if token.kind != "id":
                continue
            text = token.text
            if java.is_op(index - 1, "::"):
                if text == "new":
                    if java.is_id(index - 2):
                        self._add_site(java, index - 2, java.text(index - 2), "new", None, None)
                else:
                    self._add_site(java, index, text, "ref", index - 1, None)
                continue
            if text == "new":
                cursor = index + 1
                if not java.is_id(cursor):
                    continue
                while java.is_op(cursor + 1, ".") and java.is_id(cursor + 2):
                    cursor += 2
                after = cursor + 1
                if java.is_op(after, "<"):
                    after = java.skip_angles(after)
                    if after is None:
                        continue
                if java.is_op(after, "("):
                    close = java.matches[after]
                    self._add_site(java, cursor, java.text(cursor), "new", None, java.split_top_level(after + 1, close))
                continue
            if not java.is_op(index + 1, "("):
                continue
            close = java.matches[index + 1]
            if text in ("this", "super"):
                if java.is_op(index - 1, "{") or java.is_op(index - 1, ";") or java.is_op(index - 1, "}"):
                    self._add_site(java, index, text, text, None, java.split_top_level(index + 2, close))
                continue
            if text in KEYWORDS or java.is_op(index - 1, "@"):
                continue
            cursor = index
            while java.is_op(cursor - 1, ".") and java.is_id(cursor - 2):
                cursor -= 2
            if java.text(cursor - 1) == "new":
                continue
            if java.is_op(close + 1, "{") or java.text(close + 1) == "throws":
                continue  # local/anonymous-class method declaration
            if java.is_id(index - 1) and java.text(index - 1) not in NOT_A_TYPE_START:
                continue  # abstract method declaration inside a body
            dot = index - 1 if java.is_op(index - 1, ".") else None
            self._add_site(java, index, text, "call", dot, java.split_top_level(index + 2, close))

    # -- hierarchy -------------------------------------------------------

    def super_names(self, info):
        key = id(info)
        if key not in self._supers:
            result = set()
            stack = list(info.supers)
            while stack:
                name = stack.pop()
                if name in result:
                    continue
                result.add(name)
                for other in self.types_by_name.get(name, ()):
                    stack.extend(other.supers)
            self._supers[key] = result
        return self._supers[key]

    def super_names_of(self, name):
        result = set()
        for info in self.types_by_name.get(name, ()):
            result |= self.super_names(info)
        return result

    def compat(self, info):
        """Simple names of every static receiver type through which info's methods may be invoked."""
        key = id(info)
        if key not in self._compat:
            names = set([info.name]) | self.super_names(info)
            stack = [info.name]
            while stack:
                for sub in self.sub_index.get(stack.pop(), ()):
                    if sub not in names:
                        names.add(sub)
                        stack.append(sub)
            names.discard("Object")
            self._compat[key] = names
        return self._compat[key]

    def hierarchy_types(self, info):
        types = [info]
        for name in sorted(self.super_names(info)):
            types.extend(self.types_by_name.get(name, ()))
        return types

    def declares(self, info, name):
        key = (id(info), name)
        if key not in self._declares:
            self._declares[key] = any(
                method.name == name for each in self.hierarchy_types(info) for method in each.methods
            )
        return self._declares[key]

    def lexical_type(self, info, name):
        while info is not None:
            if self.declares(info, name):
                return info
            info = info.outer
        return None

    def methods_in_hierarchy(self, info, name, arity):
        return [
            method
            for each in self.hierarchy_types(info)
            for method in each.methods
            if method.name == name and (arity is None or _arity_ok(method, arity))
        ]

    # -- expression typing -----------------------------------------------

    def resolve_type_var(self, jtype, method, owner):
        if jtype is None or jtype.kind != T_KNOWN or jtype.name.endswith("[]"):
            return jtype
        scopes = []
        if method is not None:
            scopes.append(method.type_params)
        info = method.owner if method is not None else owner
        while info is not None:
            scopes.append(info.type_params)
            info = info.outer
        for scope in scopes:
            if jtype.name in scope:
                bound = scope[jtype.name]
                return _known(bound) if bound else _known("Object")
        return jtype

    def resolve_name(self, java, method, owner, name, at):
        declaration = java.lookup(method, name, at, False, owner)
        if declaration is not None:
            context_method = method if declaration.kind != "field" else None
            return declaration, (java, context_method, method.owner if method is not None else owner)
        info = method.owner if method is not None else owner
        while info is not None:
            for super_name in sorted(self.super_names(info)):
                for other in self.types_by_name.get(super_name, ()):
                    if name in other.fields:
                        return other.fields[name], (other.java, None, other)
            info = info.outer
        return None

    def declaration_type(self, found, depth):
        declaration, (java, method, owner) = found
        if declaration.type_name == "var":
            if declaration.init is None:
                return None
            start, end = declaration.init
            value = self.type_expr(java, method, owner, start, end, depth + 1)
            return _iterated_element(value) if declaration.kind == "foreach" else value
        return self.resolve_type_var(declaration.jtype, method, owner)

    def type_expr(self, java, method, owner, start, end, depth=0):
        try:
            return self._type_expr(java, method, owner, start, end, depth)
        except (IndexError, KeyError, ValueError, TypeError, AttributeError):
            return None

    def _type_expr(self, java, method, owner, start, end, depth):
        if start >= end or depth > 8:
            return None
        if end - start == 1:
            token = java.tokens[start]
            if token.kind == "num":
                return _number_type(token.text)
            if token.kind == "str":
                return _known("String")
            if token.kind == "chr":
                return _known("char")
            if token.text in ("true", "false"):
                return _known("boolean")
            if token.text == "null":
                return _known("null")
        if java.is_op(start, "("):
            close = java.matches[start]
            if close + 1 < end and _is_cast(java, start, close):
                parsed = java.jtype_at(list(range(start + 1, close)), 0)
                if parsed is not None and parsed[1] == close - start - 1:
                    return self.resolve_type_var(parsed[0], method, owner)
        return self._type_chain(java, method, owner, start, end, depth)

    def _type_chain(self, java, method, owner, start, end, depth):
        info = method.owner if method is not None else owner
        position = start
        token = java.tokens[position]
        if token.kind == "id" and token.text == "new":
            cursor = position + 1
            if not java.is_id(cursor):
                return None
            type_start = cursor
            while cursor + 2 < end and java.is_op(cursor + 1, ".") and java.is_id(cursor + 2):
                cursor += 2
            after = cursor + 1
            if java.is_op(after, "<"):
                after = java.skip_angles(after)
                if after is None:
                    return None
            parsed = java.jtype_at(list(range(type_start, after)), 0)
            if parsed is None:
                return None
            if java.is_op(after, "("):
                position = java.matches[after] + 1
                if position < end and java.is_op(position, "{"):
                    position = java.matches[position] + 1
                current = parsed[0]
            elif java.is_op(after, "["):
                dimensions = 0
                while after < end and java.is_op(after, "["):
                    after = java.matches[after] + 1
                    dimensions += 1
                if after < end and java.is_op(after, "{"):
                    after = java.matches[after] + 1
                current = JType(T_KNOWN, parsed[0].name + "[]" * dimensions, parsed[0].ids)
                position = after
            else:
                return None
        elif java.is_op(position, "("):
            close = java.matches[position]
            current = self.type_expr(java, method, owner, position + 1, close, depth + 1)
            position = close + 1
        elif token.kind == "str":
            current = _known("String")
            position += 1
        elif token.kind == "id" and token.text == "this":
            current = _known(info.name) if info is not None else None
            position += 1
        elif token.kind == "id" and token.text == "super":
            current = JType(T_SUPER, "super", info.supers if info is not None else ())
            position += 1
        elif token.kind == "id" and (token.text not in KEYWORDS or token.text in CONTEXTUAL_NAMES):
            if position + 1 < end and java.is_op(position + 1, "("):
                close = java.matches[position + 1]
                current = self._unqualified_return(java, method, owner, token.text,
                                                   java.split_top_level(position + 2, close), depth)
                position = close + 1
            else:
                found = self.resolve_name(java, method, owner, token.text, position)
                if found is not None:
                    current = self.declaration_type(found, depth)
                    position += 1
                elif token.text[:1].isupper():
                    current = JType(T_STATIC, token.text, (token.text,))
                    position += 1
                else:
                    cursor = position
                    while (cursor + 2 < end and java.is_op(cursor + 1, ".") and java.is_id(cursor + 2)
                           and not java.text(cursor + 2)[:1].isupper()):
                        cursor += 2
                    if cursor + 2 < end and java.is_op(cursor + 1, ".") and java.is_id(cursor + 2):
                        current = JType(T_STATIC, java.text(cursor + 2), (java.text(cursor + 2),))
                        position = cursor + 3
                    else:
                        return None
        else:
            return None
        while position < end:
            if current is None:
                return None
            if java.is_op(position, "["):
                current = _array_element(current)
                position = java.matches[position] + 1
                continue
            if not java.is_op(position, "."):
                return None
            following = position + 1
            if java.is_id(following) and java.is_op(following + 1, "("):
                close = java.matches[following + 1]
                current = self._call_return(java, method, owner, current, java.text(following),
                                            java.split_top_level(following + 2, close), depth)
                position = close + 1
            elif java.text(following) == "this":
                current = _known(current.name) if current.kind == T_STATIC else None
                position = following + 1
            elif java.text(following) == "class":
                current = _known("Class")
                position = following + 1
            elif java.is_id(following) and java.text(following) not in KEYWORDS:
                current = self._field_type(current, java.text(following))
                position = following + 1
            else:
                return None
        return current

    def _return_of(self, candidates, receiver):
        results = {}
        for method in candidates:
            if not method.has_return or method.return_type is None:
                return None
            returned = method.return_type
            if returned.kind == T_KNOWN and not returned.name.endswith("[]"):
                if returned.name in method.type_params:
                    bound = method.type_params[returned.name]
                    if bound is None:
                        return None
                    returned = _known(bound)
                elif returned.name in method.owner.type_params:
                    names = list(method.owner.type_params)
                    position = names.index(returned.name)
                    if receiver is None or position >= len(receiver.args) or receiver.args[position] is None:
                        bound = method.owner.type_params[returned.name]
                        if receiver is not None or bound is None:
                            return None
                        returned = _known(bound)
                    else:
                        returned = receiver.args[position]
            results[(returned.kind, returned.name, returned.ids)] = returned
        if len(results) != 1:
            return None
        return list(results.values())[0]

    def _external_of(self, infos):
        """Result of a member not declared in the source hierarchy: it comes from an external supertype."""
        ids = set()
        for info in infos:
            for each in self.hierarchy_types(info):
                ids |= each.super_ids - set(name for name in each.supers if name in self.types_by_name)
        return JType(T_EXTERNAL, "external", ids)

    def _unqualified_return(self, java, method, owner, name, args, depth):
        info = method.owner if method is not None else owner
        search = self.lexical_type(info, name)
        if search is not None:
            candidates = self.methods_in_hierarchy(search, name, len(args))
            return self._return_of(candidates, None) if candidates else None
        for type_name, member in java.static_imports:
            if member in (name, "*"):
                for other in self.types_by_name.get(type_name, ()):
                    candidates = self.methods_in_hierarchy(other, name, len(args))
                    if candidates:
                        return self._return_of(candidates, None)
        chain = []
        while info is not None:
            chain.append(info)
            info = info.outer
        return self._external_of(chain)  # inherited from an external supertype

    def _call_return(self, java, method, owner, receiver, name, args, depth):
        if receiver.kind == T_EXTERNAL:
            return JType(T_EXTERNAL, "external", receiver.ids)
        if receiver.kind == T_SUPER:
            infos = [other for each in sorted(receiver.ids) for other in self.types_by_name.get(each, ())]
        elif receiver.name.endswith("[]"):
            return JType(T_EXTERNAL, "external", receiver.ids)
        else:
            infos = self.types_by_name.get(receiver.name, ())
        if infos:
            candidates = [found for info in infos for found in self.methods_in_hierarchy(info, name, len(args))]
            if candidates:
                return self._return_of(candidates, receiver if receiver.kind == T_KNOWN else None)
            return self._external_of(infos)
        if receiver.kind == T_KNOWN:
            element = _container_element(receiver, name)
            if element is not False:
                return element
            return JType(T_EXTERNAL, "external", receiver.ids)
        if receiver.kind == T_STATIC:
            ids = set([receiver.name])
            for argument in args:
                if not argument:
                    return None
                value = self.type_expr(java, method, owner, argument[0], argument[-1] + 1, depth + 1)
                if value is None:
                    return None
                ids |= value.ids | set([value.name])
            return JType(T_EXTERNAL, "external", ids)
        return None

    def _field_type(self, receiver, name):
        if receiver.kind == T_EXTERNAL:
            return receiver
        if receiver.name.endswith("[]"):
            return _known("int") if name == "length" else None
        if receiver.kind == T_SUPER:
            return None
        infos = self.types_by_name.get(receiver.name, ())
        if infos:
            for info in infos:
                for each in self.hierarchy_types(info):
                    if name in each.fields:
                        return self.declaration_type((each.fields[name], (each.java, None, each)), 0)
            if receiver.kind == T_STATIC:
                if any(info.kind == "enum" for info in infos):
                    return _known(receiver.name)
                if name[:1].isupper():
                    return JType(T_STATIC, name, (name,))
            return self._external_of(infos)
        if receiver.kind == T_STATIC and name[:1].isupper():
            return JType(T_STATIC, name, (name,))
        return JType(T_EXTERNAL, "external", receiver.ids | set([receiver.name]))

    def receiver_type(self, site):
        if not site.receiver_done:
            site.receiver_done = True
            end = site.dot - 1
            start = _chain_start(site.java, end) if end >= 0 else end + 1
            site.receiver = (
                self.type_expr(site.java, site.method, site.owner, start, end + 1) if start <= end else None
            )
        return site.receiver

    def receiver_text(self, site):
        end = site.dot - 1
        start = _chain_start(site.java, end) if end >= 0 else end + 1
        text = normalize(site.java, start, end + 1) if start <= end else "?"
        return text if len(text) <= 60 else text[:57] + "..."

    def argument_types(self, site):
        if site.arg_types is None:
            site.arg_types = [
                self.type_expr(site.java, site.method, site.owner, argument[0], argument[-1] + 1) if argument else None
                for argument in site.args
            ]
        return site.arg_types

    # -- edges -----------------------------------------------------------

    def _plausible(self, receiver, target_owner):
        if receiver is None:
            return "unknown"
        names = self.compat(target_owner)
        if receiver.kind in (T_KNOWN, T_STATIC):
            return "yes" if receiver.name in names else "no"
        if receiver.kind == T_SUPER:
            return "yes" if receiver.ids & names else "no"
        return "unknown" if receiver.ids & names else "no"

    def _overloads(self, target, arity):
        visible = set([target.owner.name]) | self.super_names(target.owner)
        signature = tuple(param.type_name for param in target.params)
        return [
            other
            for other in self.methods_by_name.get(target.name, ())
            if other is not target
            and other.arity_known
            and other.owner is not None
            and other.owner.name in visible
            and (arity is None or _arity_ok(other, arity))
            and tuple(param.type_name for param in other.params) != signature
            and (not target.is_constructor or other.owner is target.owner)
        ]

    def _arguments_match(self, site, candidate):
        types = self.argument_types(site)
        verdict = "yes"
        for position, value in enumerate(types):
            if candidate.varargs and position >= len(candidate.params) - 1:
                parameter = candidate.params[-1].type_name
                if parameter.endswith("[]") and not (value is not None and value.name.endswith("[]")):
                    parameter = parameter[:-2]
            elif position < len(candidate.params):
                parameter = candidate.params[position].type_name
            else:
                return "no"
            if parameter in candidate.type_params or (candidate.owner is not None and parameter in candidate.owner.type_params):
                result = "maybe"
            else:
                result = self._argument_matches(value, parameter)
            if result == "no":
                return "no"
            if result == "maybe":
                verdict = "maybe"
        return verdict

    def _exact_match(self, site, candidate):
        types = self.argument_types(site)
        if candidate.varargs or len(types) != len(candidate.params):
            return False
        return all(
            value is not None and value.kind == T_KNOWN and value.name == param.type_name
            for value, param in zip(types, candidate.params)
        )

    def _argument_matches(self, value, parameter):
        if value is None or value.kind != T_KNOWN:
            return "maybe"
        argument = value.name
        if argument == "null":
            return "no" if parameter in PRIMITIVE_TYPES else "maybe"
        if argument == parameter:
            return "yes"
        if argument in PRIMITIVE_TYPES:
            if parameter in PRIMITIVE_TYPES:
                return "yes" if parameter in WIDENING.get(argument, ()) else "no"
            if parameter in (BOXES[argument], "Object"):
                return "yes"
            return "maybe" if parameter in ("Number", "Comparable", "Serializable") else "no"
        if parameter in PRIMITIVE_TYPES:
            unboxed = UNBOXES.get(argument)
            if unboxed is not None:
                return "yes" if parameter == unboxed or parameter in WIDENING.get(unboxed, ()) else "no"
            return "no" if argument == "String" or argument in self.types_by_name else "maybe"
        if parameter == "Object":
            return "yes"
        if argument.endswith("[]") or parameter.endswith("[]"):
            return "maybe"
        if argument == "String":
            return "yes" if parameter in STRING_SUPERS else "no"
        supers = self.super_names_of(argument)
        if parameter in supers:
            return "yes"
        if argument in self.types_by_name and parameter in self.types_by_name:
            return "no"
        return "maybe"

    def _resolve(self, site, target):
        """Returns (status, reason) for an edge site -> target, or None for no edge."""
        owner = target.owner
        kind = site.kind
        if site.args is not None and not _arity_ok(target, len(site.args)):
            return None
        if kind == "new":
            plausible = "yes" if site.name == owner.name else "no"
        elif kind == "this":
            plausible = "yes" if site.owner is owner else "no"
        elif kind == "super":
            plausible = "yes" if owner.name in site.owner.supers else "no"
        elif site.dot is None:
            search = self.lexical_type(site.owner, target.name)
            plausible = "no"
            if search is not None:
                plausible = "yes" if search.name in self.compat(owner) else "no"
            else:
                for type_name, member in site.java.static_imports:
                    if member in (target.name, "*") and type_name in self.compat(owner):
                        plausible = "yes"
        else:
            plausible = self._plausible(self.receiver_type(site), owner)
        if plausible == "no":
            return None
        reason = None
        if plausible == "unknown":
            reason = "%s: receiver '%s' of %s(...) has no statically resolvable type that excludes %s" % (
                site.location(), self.receiver_text(site), site.name, method_key(target))
        others = self._overloads(target, None if site.args is None else len(site.args))
        if others:
            if site.args is None:
                return EDGE_UNCERTAIN, "%s: method reference to overloaded %s" % (site.location(), method_key(target))
            if self._arguments_match(site, target) == "no":
                return None
            # An overload whose parameter types equal the argument types is
            # applicable without boxing and is always the most specific one.
            if self._exact_match(site, target):
                others = []
            elif any(self._exact_match(site, other) for other in others):
                return None
            if any(self._arguments_match(site, other) != "no" for other in others):
                return EDGE_UNCERTAIN, "%s: ambiguous overload for %s(...) among %s" % (
                    site.location(), site.name,
                    ", ".join(sorted(method_key(each) for each in [target] + others)))
        if reason is not None:
            return EDGE_UNCERTAIN, reason
        return EDGE_RESOLVED, None

    def callers(self, target):
        key = id(target)
        if key in self._callers:
            return self._callers[key]
        edges = []
        name = target.name
        if name in ("<field-init>", "<initializer>") or name.startswith("<block:"):
            for site in self.new_sites.get(target.owner.name, ()):
                edges.append((site, EDGE_RESOLVED, None))
        elif not name.startswith("<"):
            if target.is_constructor:
                sites = list(self.new_sites.get(name, ())) + self.constructor_sites
            else:
                sites = self.sites_by_name.get(name, ())
            for site in sites:
                resolution = self._resolve(site, target)
                if resolution is not None:
                    edges.append((site, resolution[0], resolution[1]))
            for parsed in self.unparsed:
                if name in parsed.call_names:
                    edges.append((None, EDGE_IMMEDIATE, "%s: unparsed file (%s) mentions a call named %s" % (
                        parsed.label, parsed.error, name)))
        self._callers[key] = edges
        return edges

    # -- reachability ----------------------------------------------------

    def _argument_provenance(self, site, target, tracked):
        caller = site.caller
        if site.args is None or caller.body_open < 0 or site.method is None:
            return None, PROV_UNPROVEN_ARGUMENT
        if target.varargs and tracked >= len(target.params) - 1:
            return None, PROV_UNPROVEN_ARGUMENT
        if tracked >= len(site.args) or not site.args[tracked]:
            return None, PROV_UNPROVEN_ARGUMENT
        argument = site.args[tracked]
        found = []
        classification, _ = _classify_index(site.java, caller, argument[0], argument[-1] + 1, site.index, found)
        if classification == PROVEN_CONSTANT_OPERAND:
            return None, PROV_OPERATION_CONSTANT
        if classification == DIRECT_CONSTANT:
            return None, PROV_DIRECT_CONSTANT
        if classification == METHOD_PARAMETER and found and found[-1] in caller.params:
            return caller.params.index(found[-1]), None
        if classification in (RUNTIME_NAME_DERIVED, LOOP_INDEX):
            return None, classification
        return None, PROV_UNPROVEN_ARGUMENT

    @staticmethod
    def _root_provenance(root, tracked):
        proof = _prove_constant_operand(root.java, root, root.params[tracked])
        return PROV_OPERATION_CONSTANT if proof is not None else PROV_RUNTIME_OPERAND

    @staticmethod
    def _chain(parent, node):
        chain = []
        while node is not None:
            entry = parent[node]
            label = method_key(node[0][0])
            if entry is not None and entry[2].opaque:
                label += " [lambda/anonymous body]"
            chain.append(label)
            node = entry[0] if entry is not None else None
        return chain

    @staticmethod
    def _uncertain_reason(parent, node):
        while node is not None:
            entry = parent[node]
            if entry is None:
                break
            if entry[1] is not None:
                return entry[1]
            node = entry[0]
        return "uncertain call edge"

    def classify(self, sink):
        """Sets the PE reachability fields of one sink."""
        method = sink._method
        sink.pe_provenance = []
        sink.pe_paths = []
        sink.pe_call_chain = []
        sink.pe_boundary_paths = []
        sink.pe_unknown = []
        if sink.classification == UNKNOWN or method is None:
            sink.pe_reachability = PE_REACHABILITY_UNKNOWN
            sink.pe_unknown = ["local index provenance is UNKNOWN: %s" % sink.reason]
            return
        if sink.classification == TRUFFLE_BOUNDARY:
            sink.pe_reachability = BOUNDARY_CUT
            sink.pe_call_chain = [method_key(method)]
            sink.pe_boundary_paths = [list(sink.pe_call_chain)]
            return
        if sink.classification == METHOD_PARAMETER:
            if sink._param is None or sink._param not in method.params:
                sink.pe_reachability = PE_REACHABILITY_UNKNOWN
                sink.pe_unknown = ["METHOD_PARAMETER index does not resolve to a parameter of %s" % method_key(method)]
                return
            start = (method, method.params.index(sink._param), None)
        else:
            start = (method, None, LOCAL_PROVENANCE[sink.classification])
        first = (start, False)
        parent = {first: None}
        queue = deque([first])
        terminals = {}
        roots = {}
        cuts = []
        has_cut = False
        unknown = []

        def add_unknown(reason):
            self.unknown_edges.add(reason.split("; reaches PE root", 1)[0])
            if reason not in unknown:
                unknown.append(reason)

        while queue:
            node = queue.popleft()
            (current, tracked, provenance), uncertain = node
            if is_pe_root(current):
                final = provenance if tracked is None else self._root_provenance(current, tracked)
                if uncertain:
                    add_unknown("%s; reaches PE root %s" % (self._uncertain_reason(parent, node), method_key(current)))
                else:
                    if final not in terminals:
                        terminals[final] = self._chain(parent, node)
                    roots.setdefault(final, set()).add(method_key(current))
                continue
            for site, status, reason in self.callers(current):
                if status == EDGE_IMMEDIATE:
                    add_unknown(reason)
                    continue
                caller = site.caller
                edge_uncertain = uncertain or status == EDGE_UNCERTAIN
                if is_truffle_boundary(caller):
                    if not edge_uncertain:
                        has_cut = True
                        chain = [method_key(caller)] + self._chain(parent, node)
                        if len(cuts) < 3 and chain not in cuts:
                            cuts.append(chain)
                    continue
                if tracked is None:
                    state = (caller, None, provenance)
                else:
                    next_tracked, next_provenance = self._argument_provenance(site, current, tracked)
                    state = (caller, next_tracked, next_provenance)
                following = (state, edge_uncertain)
                if following in parent or (edge_uncertain and (state, False) in parent):
                    continue
                parent[following] = (node, reason if status == EDGE_UNCERTAIN else None, site)
                if len(parent) > SEARCH_STATE_LIMIT:
                    add_unknown("reachability search exceeded %d states" % SEARCH_STATE_LIMIT)
                    queue.clear()
                    break
                queue.append(following)
        sink.pe_provenance = sorted(terminals, key=PROVENANCES.index)
        sink.pe_paths = [
            {"provenance": provenance, "roots": len(roots[provenance]), "chain": terminals[provenance]}
            for provenance in sink.pe_provenance
        ]
        sink.pe_boundary_paths = cuts
        sink.pe_unknown = unknown
        risky = [provenance for provenance in sink.pe_provenance if provenance not in CONSTANT_PROVENANCES]
        if unknown:
            sink.pe_reachability = PE_REACHABILITY_UNKNOWN
        elif risky:
            sink.pe_reachability = PE_REACHABLE_RISK
            sink.pe_call_chain = min((terminals[each] for each in risky), key=len)
        elif terminals:
            sink.pe_reachability = PE_REACHABLE_PROVEN_CONSTANT
            sink.pe_call_chain = min(terminals.values(), key=len)
        elif has_cut:
            sink.pe_reachability = BOUNDARY_CUT
            sink.pe_call_chain = cuts[0]
        else:
            sink.pe_reachability = NOT_PE_REACHABLE


def _arity_ok(method, arity):
    if not method.arity_known:
        return True
    if method.varargs:
        return arity >= len(method.params) - 1
    return arity == len(method.params)


def _number_type(text):
    if re.match(r"^(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*)[lL]$", text):
        return _known("long")
    if _is_int_literal(Token("num", text, 0)):
        return _known("int")
    if text[-1:] in ("f", "F"):
        return _known("float")
    return _known("double")


def _is_cast(java, open_index, close_index):
    first = open_index + 1
    if not java.is_id(first):
        return False
    text = java.text(first)
    if not (text[:1].isupper() or text in PRIMITIVE_TYPES):
        return False
    for index in range(first, close_index):
        token = java.tokens[index]
        if not (token.kind == "id" or (token.kind == "op" and token.text in (".", "<", ">", ",", "?", "[", "]", "&"))):
            return False
    following = java.tokens[close_index + 1]
    return following.kind in ("id", "str", "num", "chr") or (following.kind == "op" and following.text == "(")


def _array_element(value):
    if value is not None and value.kind == T_KNOWN and value.name.endswith("[]"):
        return JType(T_KNOWN, value.name[:-2], value.ids)
    if value is not None and value.kind == T_EXTERNAL:
        return value
    return None


def _iterated_element(value):
    if value is None:
        return None
    if value.kind == T_KNOWN and value.name.endswith("[]"):
        return _array_element(value)
    if value.kind == T_KNOWN and value.args:
        return value.args[0]
    if value.kind == T_EXTERNAL:
        return value
    return None


def _container_element(receiver, name):
    """Element type for a known external container method; False if not applicable."""
    if receiver.name in MAP_TYPES and len(receiver.args) == 2 and name in MAP_VALUE_METHODS:
        return receiver.args[1]
    if receiver.name == "Entry" and len(receiver.args) == 2 and name in ("getKey", "getValue"):
        return receiver.args[0 if name == "getKey" else 1]
    if receiver.name not in MAP_TYPES and len(receiver.args) == 1 and name in ELEMENT_METHODS:
        return receiver.args[0]
    return False


def analyze_reachability(sinks: List[Sink], parsed_files: List[ParsedFile]) -> CallGraph:
    graph = CallGraph(parsed_files)
    for sink in sinks:
        graph.classify(sink)
    return graph


# --------------------------------------------------------------------------
# Baseline verification
# --------------------------------------------------------------------------


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
        if not isinstance(entry, dict):
            raise GuardError("baseline entry %d is not an object" % position)
        expected = set(BASELINE_KEYS) | {"rationale"}
        if set(entry) != expected:
            raise GuardError("baseline entry %d must have exactly the keys %s" % (position, sorted(expected)))
        for key in BASELINE_KEYS:
            value = entry[key]
            if key == "occurrence":
                if not isinstance(value, int) or isinstance(value, bool) or value < 0:
                    raise GuardError("baseline entry %d: occurrence must be a non-negative integer" % position)
            elif not isinstance(value, str) or not value or "*" in value:
                raise GuardError("baseline entry %d: %s must be a non-empty exact (non-wildcard) string" % (position, key))
        if not isinstance(entry["rationale"], str) or not entry["rationale"].strip():
            raise GuardError("baseline entry %d: rationale must be non-empty" % position)
        if entry["classification"] not in RISK_CLASSIFICATIONS:
            raise GuardError(
                "baseline entry %d: classification %s is not a baselineable risk class %s"
                % (position, entry["classification"], list(RISK_CLASSIFICATIONS))
            )
    return entries


def baseline_entry_key(entry: dict):
    return tuple(entry[key] for key in BASELINE_KEYS)


def verify(sinks: List[Sink], entries: List[dict]) -> List[str]:
    failures = []
    seen = {}
    for entry in entries:
        key = baseline_entry_key(entry)
        if key in seen:
            failures.append("DUPLICATE_BASELINE_ENTRY %s" % "|".join(str(part) for part in key))
        seen[key] = entry
    matched = set()
    for sink in sinks:
        if sink.classification == UNKNOWN:
            sink.status = STATUS_UNKNOWN
            failures.append("UNKNOWN %s:%d %s.%s %s(..., %s): %s" % (
                sink.path, sink.line, sink.type_name, sink.method, sink.operation, sink.index, sink.reason))
        elif sink.classification in SAFE_CLASSIFICATIONS:
            sink.status = STATUS_SAFE
        elif sink.key() in seen:
            sink.status = STATUS_BASELINED
            matched.add(sink.key())
        else:
            sink.status = STATUS_NEW
            failures.append("NEW_UNBASELINED_RISK %s:%d %s.%s %s(..., %s) %s: %s" % (
                sink.path, sink.line, sink.type_name, sink.method, sink.operation, sink.index,
                sink.classification, sink.reason))
    for key in seen:
        if key not in matched:
            failures.append("STALE_BASELINE_ENTRY %s" % "|".join(str(part) for part in key))
    return failures


def counts(sinks: List[Sink]) -> Dict[str, int]:
    result = {"TOTAL_LOCAL_RANGE_SINKS": len(sinks)}
    for classification in CLASSIFICATIONS:
        result[classification] = sum(1 for sink in sinks if sink.classification == classification)
    result["PROVEN_SAFE"] = sum(1 for sink in sinks if sink.status == STATUS_SAFE)
    result["BASELINED_RISKS"] = sum(1 for sink in sinks if sink.status == STATUS_BASELINED)
    result["NEW_UNBASELINED_RISKS"] = sum(1 for sink in sinks if sink.status == STATUS_NEW)
    return result


def load_reachability_baseline(path: Path) -> List[dict]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise GuardError("cannot read reachability baseline %s: %s" % (path, error))
    if not isinstance(data, dict) or data.get("schema") != REACHABILITY_BASELINE_SCHEMA:
        raise GuardError("reachability baseline %s: schema must be %s" % (path, REACHABILITY_BASELINE_SCHEMA))
    entries = data.get("entries")
    if not isinstance(entries, list):
        raise GuardError("reachability baseline %s: 'entries' must be a list" % path)
    expected = set(REACHABILITY_KEYS) | {"rationale"}
    for position, entry in enumerate(entries):
        if not isinstance(entry, dict) or set(entry) != expected:
            raise GuardError("reachability entry %d must be an object with exactly the keys %s" % (position, sorted(expected)))
        for key in BASELINE_KEYS:
            value = entry[key]
            if key == "occurrence":
                if not isinstance(value, int) or isinstance(value, bool) or value < 0:
                    raise GuardError("reachability entry %d: occurrence must be a non-negative integer" % position)
            elif not isinstance(value, str) or not value or "*" in value:
                raise GuardError("reachability entry %d: %s must be a non-empty exact (non-wildcard) string" % (position, key))
        if entry["classification"] not in CLASSIFICATIONS or entry["classification"] == UNKNOWN:
            raise GuardError("reachability entry %d: classification must be a determinate local class" % position)
        if entry["pe_reachability"] not in PE_CLASSIFICATIONS or entry["pe_reachability"] == PE_REACHABILITY_UNKNOWN:
            raise GuardError(
                "reachability entry %d: pe_reachability %r is not baselineable (%s is always a failure)"
                % (position, entry["pe_reachability"], PE_REACHABILITY_UNKNOWN))
        provenance = entry["pe_provenance"]
        if (
            not isinstance(provenance, list)
            or any(value not in PROVENANCES for value in provenance)
            or provenance != sorted(set(provenance), key=PROVENANCES.index)
        ):
            raise GuardError("reachability entry %d: pe_provenance must be a canonical list of %s" % (position, list(PROVENANCES)))
        chain = entry["pe_call_chain"]
        if not isinstance(chain, list) or any(not isinstance(value, str) or not value or "*" in value for value in chain):
            raise GuardError("reachability entry %d: pe_call_chain must be a list of exact method identities" % position)
        if not isinstance(entry["rationale"], str) or not entry["rationale"].strip():
            raise GuardError("reachability entry %d: rationale must be non-empty" % position)
    return entries


def reachability_entry(sink: Sink) -> dict:
    entry = dict(zip(BASELINE_KEYS, sink.key()))
    entry["pe_reachability"] = sink.pe_reachability
    entry["pe_provenance"] = list(sink.pe_provenance)
    entry["pe_call_chain"] = list(sink.pe_call_chain)
    if sink.pe_reachability == PE_REACHABLE_RISK:
        risky = [each for each in sink.pe_provenance if each not in CONSTANT_PROVENANCES]
        rationale = "PE-reachable with non-constant index (%s): %s" % (
            ", ".join(risky), "; ".join(PROVENANCE_REASONS[each] for each in risky))
    elif sink.pe_reachability == PE_REACHABLE_PROVEN_CONSTANT:
        rationale = "PE-reachable only with structurally proven constant indices (%s)" % ", ".join(sink.pe_provenance)
    elif sink.pe_reachability == BOUNDARY_CUT:
        rationale = "every PE path is cut by @TruffleBoundary %s" % short_key(sink.pe_call_chain[0])
    else:
        rationale = "no call path from a Bytecode DSL operation root"
    entry["rationale"] = rationale
    return entry


def verify_reachability(sinks: List[Sink], entries: Optional[List[dict]]) -> List[str]:
    failures = []
    for sink in sinks:
        if sink.pe_reachability == PE_REACHABILITY_UNKNOWN:
            sink.pe_status = PE_STATUS_UNKNOWN
            if sink.classification != UNKNOWN:
                failures.append("PE_REACHABILITY_UNKNOWN %s:%d %s.%s %s(..., %s): %s" % (
                    sink.path, sink.line, sink.type_name, sink.method, sink.operation, sink.index,
                    " | ".join(sink.pe_unknown[:5]) + (" | ..." if len(sink.pe_unknown) > 5 else "")))
    if entries is None:
        return failures
    seen = {}
    for entry in entries:
        key = baseline_entry_key(entry)
        if key in seen:
            failures.append("DUPLICATE_REACHABILITY_ENTRY %s" % "|".join(str(part) for part in key))
        seen[key] = entry
    matched = set()
    for sink in sinks:
        if sink.pe_reachability == PE_REACHABILITY_UNKNOWN:
            continue
        entry = seen.get(sink.key())
        identity = "%s:%d %s.%s %s(..., %s)" % (
            sink.path, sink.line, sink.type_name, sink.method, sink.operation, sink.index)
        if entry is None:
            sink.pe_status = PE_STATUS_NEW
            failures.append("NEW_UNBASELINED_REACHABILITY %s %s %s" % (
                identity, sink.pe_reachability, " -> ".join(short_key(each) for each in sink.pe_call_chain)))
            continue
        matched.add(sink.key())
        actual = reachability_entry(sink)
        drift = [
            key for key in ("pe_reachability", "pe_provenance", "pe_call_chain") if entry[key] != actual[key]
        ]
        if drift:
            sink.pe_status = PE_STATUS_DRIFT
            failures.append("REACHABILITY_DRIFT %s: %s" % (identity, "; ".join(
                "%s expected %s actual %s" % (key, json.dumps(entry[key]), json.dumps(actual[key])) for key in drift)))
        else:
            sink.pe_status = PE_STATUS_BASELINED
    for key in seen:
        if key not in matched:
            failures.append("STALE_REACHABILITY_ENTRY %s" % "|".join(str(part) for part in key))
    return failures


def check(source: Path, baseline: Path, report: Optional[Path], label_root: Path, out=sys.stdout,
          reachability_baseline: Optional[Path] = None) -> int:
    try:
        sinks, parsed = scan_tree(source, label_root)
        entries = load_baseline(baseline)
        reachability_entries = (
            load_reachability_baseline(reachability_baseline) if reachability_baseline is not None else None
        )
    except GuardError as error:
        print("local-range-pe-guard: ANALYSIS_FAILED: %s" % error, file=out)
        return 1
    failures = verify(sinks, entries)
    totals = counts(sinks)
    totals["STALE_BASELINE_ENTRIES"] = sum(1 for failure in failures if failure.startswith("STALE_"))
    graph = analyze_reachability(sinks, parsed)
    reachability_failures = verify_reachability(sinks, reachability_entries)
    for classification in PE_CLASSIFICATIONS:
        totals[classification] = sum(1 for sink in sinks if sink.pe_reachability == classification)
    totals["PE_ROOTS"] = len(graph.roots)
    totals["CALL_GRAPH_METHODS"] = graph.method_count
    totals["CALL_GRAPH_SITES"] = graph.site_count
    totals["CALL_GRAPH_AMBIGUITIES"] = len(graph.unknown_edges)
    totals["UNPARSED_FILES"] = len(graph.unparsed)
    totals["NEW_UNBASELINED_REACHABILITY"] = sum(1 for sink in sinks if sink.pe_status == PE_STATUS_NEW)
    totals["REACHABILITY_DRIFT"] = sum(1 for sink in sinks if sink.pe_status == PE_STATUS_DRIFT)
    totals["STALE_REACHABILITY_ENTRIES"] = sum(
        1 for failure in reachability_failures if failure.startswith("STALE_REACHABILITY_ENTRY"))
    failures = failures + reachability_failures
    candidates = [reachability_entry(sink) for sink in sinks if sink.pe_reachability != PE_REACHABILITY_UNKNOWN]
    candidate_path = None
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        document = {
            "schema": REPORT_SCHEMA,
            "totals": totals,
            "result": "PASS" if not failures else "FAIL",
            "reachability_baseline_checked": reachability_entries is not None,
            "failures": failures,
            "pe_roots": sorted(method_key(root) for root in graph.roots),
            "call_graph_unknown_edges": sorted(graph.unknown_edges),
            "unparsed_files": [{"path": each.label, "error": each.error} for each in graph.unparsed],
            "sinks": [sink.to_json() for sink in sinks],
        }
        report.write_text(json.dumps(document, indent=2, sort_keys=False) + "\n", encoding="utf-8")
        candidate_path = report.parent / REACHABILITY_CANDIDATE_NAME
        candidate = {
            "schema": REACHABILITY_BASELINE_SCHEMA,
            "description": "Candidate generated by the guard from the current source; review before adopting.",
            "entries": candidates,
        }
        candidate_path.write_text(json.dumps(candidate, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    print("local-range-pe-guard: inventory of indexed %s operations" % ACCESSOR_TYPE, file=out)
    print("  %-20s %-23s %-28s %s" % ("LOCAL_STATUS", "LOCAL_INDEX_PROVENANCE", "PE_REACHABILITY", "SINK"), file=out)
    for sink in sinks:
        print(
            "  %-20s %-23s %-28s %s:%d %s.%s %s(..., %s)"
            % (sink.status, sink.classification, sink.pe_reachability, sink.path.rsplit("/", 1)[-1], sink.line,
               sink.type_name.rsplit(".", 1)[-1], sink.method.split("(", 1)[0], sink.operation, sink.index),
            file=out,
        )
    risks = [sink for sink in sinks if sink.pe_reachability == PE_REACHABLE_RISK]
    if risks:
        print("local-range-pe-guard: PE-reachable risks (representative shortest call chains)", file=out)
    for sink in risks:
        print("  PE_REACHABLE_RISK %s.%s %s(..., %s)  local=%s" % (
            sink.type_name.rsplit(".", 1)[-1], sink.method.split("(", 1)[0], sink.operation, sink.index,
            sink.classification), file=out)
        for path in sink.pe_paths:
            print("    index=%s roots=%d%s" % (
                path["provenance"], path["roots"],
                "" if path["provenance"] in CONSTANT_PROVENANCES else "  (not proven constant: %s)"
                % PROVENANCE_REASONS[path["provenance"]]), file=out)
            chain = path["chain"]
            print("      " + short_key(chain[0]), file=out)
            for element in chain[1:]:
                print("      -> " + short_key(element), file=out)
            print("      -> %s.%s(..., %s)" % (ACCESSOR_TYPE, sink.operation, sink.index), file=out)
    for key, value in totals.items():
        print("%s=%d" % (key, value), file=out)
    if failures:
        print("local-range-pe-guard: FAIL", file=out)
        for failure in failures:
            print("  " + failure, file=out)
        new = [sink for sink in sinks if sink.status == STATUS_NEW]
        if new:
            print("Exact baseline entries for the new risks (review before adding):", file=out)
            for sink in new:
                entry = dict((key, value) for key, value in zip(BASELINE_KEYS, sink.key()))
                entry["rationale"] = sink.reason
                print("  " + json.dumps(entry, sort_keys=False) + ",", file=out)
        changed = [sink for sink in sinks if sink.pe_status in (PE_STATUS_NEW, PE_STATUS_DRIFT)]
        if changed and len(changed) <= 10:
            print("Exact reachability entries for new/drifted sinks (review before adopting):", file=out)
            for sink in changed:
                print("  " + json.dumps(reachability_entry(sink), sort_keys=False) + ",", file=out)
        if changed and candidate_path is not None:
            print("Complete candidate reachability baseline: %s" % candidate_path, file=out)
        return 1
    print("local-range-pe-guard: PASS", file=out)
    print("  static sink inventory complete", file=out)
    print("  UNKNOWN=0", file=out)
    print("  baseline consistent", file=out)
    print("  PE reachability analysis complete", file=out)
    print("  PE_REACHABILITY_UNKNOWN=0", file=out)
    if reachability_entries is not None:
        print("  reachability baseline consistent", file=out)
    else:
        print("  reachability baseline NOT CHECKED (no --reachability-baseline given)", file=out)
    print("NOTE:", file=out)
    print("  baselined risks remain and are NOT proven PE-safe", file=out)
    print("  PE_REACHABLE_RISK=%d site(s) remain and are NOT proven safe" % totals[PE_REACHABLE_RISK], file=out)
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    subparsers = parser.add_subparsers(dest="command")
    check_parser = subparsers.add_parser("check")
    check_parser.add_argument("--source", required=True, type=Path)
    check_parser.add_argument("--baseline", required=True, type=Path)
    check_parser.add_argument("--reachability-baseline", type=Path)
    check_parser.add_argument("--report", type=Path)
    check_parser.add_argument("--label-root", type=Path, default=Path("."))
    arguments = parser.parse_args(argv)
    if arguments.command != "check":
        parser.print_help()
        return 2
    return check(arguments.source, arguments.baseline, arguments.report, arguments.label_root,
                 reachability_baseline=arguments.reachability_baseline)


if __name__ == "__main__":
    sys.exit(main())
