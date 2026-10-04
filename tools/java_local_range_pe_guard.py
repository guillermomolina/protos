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

Subcommand:

  check --source DIR --baseline FILE [--report FILE]

Standard library only; one process; no compilation, Maven, Graal, or network.
"""

from __future__ import print_function

import argparse
import json
import re
import sys
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
REPORT_SCHEMA = "protos-local-range-pe-guard-report-v1"
BASELINE_KEYS = ("path", "class", "method", "operation", "index", "classification", "occurrence")

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


class TypeInfo(object):
    def __init__(self, name, qualified, annotations, outer):
        self.name = name
        self.qualified = qualified
        self.annotations = annotations
        self.outer = outer
        self.fields = {}  # name -> Declaration


class Declaration(object):
    def __init__(self, name, type_name, index, kind, init=None, scope_end=None, modifiers=()):
        self.name = name
        self.type_name = type_name
        self.index = index
        self.kind = kind  # "param" | "local" | "for" | "foreach" | "field"
        self.init = init  # (start, end) exclusive token range of the initializer
        self.scope_end = scope_end
        self.modifiers = frozenset(modifiers)


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

    @property
    def signature(self):
        return "%s(%s)" % (self.name, ",".join(param.type_name for param in self.params))


class JavaFile(object):
    def __init__(self, label: str, tokens: List[Token]):
        self.label = label
        self.tokens = tokens
        self.matches, self.enclosing = match_brackets(tokens, label)
        self.methods = []  # type: List[Method]
        self.opaque_members = []  # (start, end, owner) field initializers, enum constant bodies
        self.types = []
        self._parse_compilation_unit()
        for method in self.methods:
            self._collect_body(method)

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
                    self.opaque_members.append((index, close, owner))
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
            self.opaque_members.append((open_index, close_index, owner))
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
            self.methods.append(method)
            return True
        name = "<initializer>"
        if rest and all(self.text(index) in MODIFIERS for index in rest):
            name = "<static-initializer>" if any(self.text(index) == "static" for index in rest) else name
        elif rest:
            # Compact record constructor or an unrecognized shape.
            name = "<block:%s>" % " ".join(self.text(index) for index in rest)
        self.methods.append(Method(owner, name, [], annotations, open_index, close_index))
        return True

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
        if any(self.is_op(rest[p], "(") for p in range(position, assignment)):
            return  # abstract/interface method or annotation element
        parsed = self.parse_type(rest, position)
        if parsed is None:
            return
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
                    self.opaque_members.append((init[0] - 1, init[1], owner))
            owner.fields[self.text(name_index)] = Declaration(
                self.text(name_index), type_name, name_index, "field", init, None, modifiers
            )
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
        if self.text(name_index) in KEYWORDS:
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
        return Declaration(self.text(name_index), type_name, name_index, kind, init, scope_end)

    # -- lookup ----------------------------------------------------------

    def method_at(self, index) -> Optional[Method]:
        best = None
        for method in self.methods:
            if method.body_open < index < method.body_close:
                if best is None or method.body_open > best.body_open:
                    best = method
        return best

    def opaque_member_at(self, index):
        for start, end, owner in self.opaque_members:
            if start < index < end:
                return owner
        return None

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


def _classify_identifier(java: JavaFile, method: Method, name: str, at: int, seen) -> Tuple[str, str]:
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
        classification, reason = _classify_identifier(java, method, java.text(start), declaration.index, seen)
        return classification, "local alias '%s' = %s; %s" % (name, init_text, reason)
    return UNKNOWN, "local '%s' = %s (unrecognized provenance)" % (name, init_text)


def _classify_index(java: JavaFile, method: Method, start: int, end: int, at: int) -> Tuple[str, str]:
    count = end - start
    if count == 1 and _is_int_literal(java.tokens[start]):
        return DIRECT_CONSTANT, "integer literal"
    if count == 1 and java.is_id(start) and java.text(start) not in KEYWORDS:
        return _classify_identifier(java, method, java.text(start), at, frozenset())
    if count == 3 and java.text(start) == "this" and java.is_op(start + 1, ".") and java.is_id(start + 2):
        return UNKNOWN, "index read from field 'this.%s'" % java.text(start + 2)
    return UNKNOWN, "compound index expression"


def analyze_file(path_label: str, source: str) -> List[Sink]:
    tokens = lex(source, path_label)
    candidates = []
    for index, token in enumerate(tokens):
        if token.kind != "id" or token.text not in INDEXED_ARITY:
            continue
        if index == 0 or tokens[index - 1].kind != "op" or tokens[index - 1].text not in (".", "::"):
            continue
        candidates.append(index)
    if not candidates:
        return []
    java = JavaFile(path_label, tokens)
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
        classification, reason = _classify_index(java, method, index_tokens[0], index_tokens[-1] + 1, index)
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


def scan(source_root: Path, label_root: Path) -> List[Sink]:
    if not source_root.is_dir():
        raise GuardError("source directory does not exist: %s" % source_root)
    sinks = []
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
        sinks.extend(analyze_file(label, source))
    sinks.sort(key=lambda sink: (sink.path, sink.line, sink.column_index))
    return sinks


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


def check(source: Path, baseline: Path, report: Optional[Path], label_root: Path, out=sys.stdout) -> int:
    try:
        sinks = scan(source, label_root)
        entries = load_baseline(baseline)
    except GuardError as error:
        print("local-range-pe-guard: ANALYSIS_FAILED: %s" % error, file=out)
        return 1
    failures = verify(sinks, entries)
    totals = counts(sinks)
    totals["STALE_BASELINE_ENTRIES"] = sum(1 for failure in failures if failure.startswith("STALE_"))
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        document = {
            "schema": REPORT_SCHEMA,
            "totals": totals,
            "result": "PASS" if not failures else "FAIL",
            "failures": failures,
            "sinks": [sink.to_json() for sink in sinks],
        }
        report.write_text(json.dumps(document, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    print("local-range-pe-guard: inventory of indexed %s operations" % ACCESSOR_TYPE, file=out)
    for sink in sinks:
        print(
            "  %-20s %-23s %s:%d %s.%s %s(..., %s)"
            % (sink.status, sink.classification, sink.path.rsplit("/", 1)[-1], sink.line,
               sink.type_name.rsplit(".", 1)[-1], sink.method.split("(", 1)[0], sink.operation, sink.index),
            file=out,
        )
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
        return 1
    print(
        "local-range-pe-guard: PASS (inventory complete, UNKNOWN=0, baseline consistent; "
        "baselined risks remain and are NOT proven PE-safe)",
        file=out,
    )
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
