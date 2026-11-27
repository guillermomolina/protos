# Markdown Standard Library

This document normatively owns `std:markdown`, an independently importable,
authority-free Standard Library MODULE. It introduces no Core or Prelude binding.
The ordinary module, invocation, object, collection and Error rules continue to
apply. The public own exports are exactly `parse` and `MarkdownLimitError`.

## Invocation and profiles

```protos
Markdown: import("std:markdown")
document: Markdown.parse(source, profile: "commonmark-0.31.2", positions: false)
```

`parse(source, profile: "commonmark-0.31.2", positions: false)` is an ordinary
synchronous source Closure. Ordinary positional/named association and defaults
apply; explicitly supplied null never selects a default. Validate in declaration
order: source is a semantic String; profile is a semantic String whose exact
value is one of the two values below; positions is canonical true or false.
Invalid arguments signal ordinary Error. No implicit conversion is performed.
Only after argument validation is the original input scalar limit checked.

* `commonmark-0.31.2`: [CommonMark 0.31.2](https://spec.commonmark.org/0.31.2/).
* `commonmark-0.31.2-gfm-extensions`: that same base plus tables (§4.10), task
  list items (§5.3), strikethrough (§6.5), and extended autolinks (§6.9) from
  the [published GFM 0.29 specification](https://github.github.com/gfm/).

CommonMark 0.31.2 prevails for shared syntax. Published GFM extension syntax,
including mailto and xmpp autolinks, controls, rather than incidental behavior
or fixture omissions of any implementation. Both matching single and double
tilde forms produce strikethrough; runs of three or more do not. No other
extensions are enabled. This is not a promise of complete GitHub.com behavior.
Malformed Markdown has its ordinary literal interpretation, not a syntax Error.

## Semantic tree

Each successful invocation returns a fresh ordinary open document object.
Every node has an own String `kind` and exactly the additional own slots below,
plus `span` only when positions is true. Children are fresh ordinary open Arrays.
There is no public Node hierarchy, automatic freezing, parent reference, cycle,
private working state, source descriptor, or custom equality/hash behavior.
Mutable nodes and Arrays are not shared between invocations. Immutable scalar
values may be shared under the ordinary value rules.

| kind | Additional own slots |
| --- | --- |
| document | children |
| paragraph | children |
| heading | level, children |
| thematicBreak | none |
| blockquote | children |
| list | ordered, start, tight, children |
| listItem | task, children |
| codeBlock | value, info |
| htmlBlock | value |
| text | value |
| emphasis | children |
| strong | children |
| codeSpan | value |
| link | destination, title, children |
| image | destination, title, children |
| softBreak | none |
| hardBreak | none |
| htmlInline | value |
| strikethrough | children |
| table | alignments, children |
| tableRow | children |
| tableCell | children |

Nodes with children always have an Array, including when empty; leaves lack a
children slot. Adjacent text nodes in one container coalesce and empty text
nodes are omitted. Adjacent HTML nodes do not coalesce as text. Syntax metadata
other than optional spans is absent. Heading level is an Integer from 1 through
6. Lists contain listItem children; ordered and tight are canonical Booleans.
Ordered-list start is the Integer spelled by the first marker, including zero;
unordered-list start is canonical null. Each item has task null ordinarily,
false for an incomplete task, and true for a completed task. Only the GFM
profile recognizes task markers, on the first paragraph of an item. The marker
must be followed by whitespace and nonempty content; an isolated marker stays
literal. The marker is removed from inline content without a checkbox node.

All value, destination, info and title values are Strings except the specified
null cases. A fenced code block without information has info `""`; indented
code has info null. The complete processed fence information is retained.
Code block content uses logical LF and includes a final LF for a physical
content line ending at EOF. An empty code block has value `""`. Code spans use
CommonMark line-ending/one-space normalization. Literal HTML retains logical
LF but acquires no synthetic final terminator. HTML is not sanitized or evaluated.

Links and images preserve the semantic destination without percent encoding,
URI normalization, resolution, or authorization. Absent titles are null;
explicitly empty titles are `""`. Images retain inline children as semantic
image descriptions. Nested links are prohibited by CommonMark; images remain
eligible where links cannot nest.

Tables contain tableRow children, with the first row the header. Every row has
the header's column count. Alignments is a fresh open Array of exactly that
count, each element `"left"`, `"center"`, `"right"` or null. The delimiter row
produces no node. Explicit cells have inline children; missing cells are
synthesized with empty children; excess cells are discarded. Table-specific
escaped pipes are handled before inline parsing, including inside code spans.

## References, Unicode and entities

Definitions throughout the document, including eligible containers, are
collected before references are resolved. Definitions produce no nodes. The
first equivalent definition in source order wins, including for forward uses.
Normalize labels by CommonMark whitespace trimming/collapsing followed by
Unicode 17.0.0 full default case folding (C and F mappings, not S or T).
No NFC/NFKC, locale conversion, general entity decoding, or Core String equality
change is implied. Label admission and length follow CommonMark.

Input is the supplied String's Unicode scalar sequence. CR, LF and CRLF have
CommonMark line semantics, with CRLF a single logical terminator. Indentation
tabs advance to four-column tab stops where required; they are not globally
expanded. U+0000 becomes U+FFFD in processed Markdown; existing U+FEFF is
preserved. Supplementary scalars count once. No Unicode normalization occurs.
Delimiter whitespace uses CommonMark Unicode Zs and its specified exceptions;
punctuation uses Unicode categories P and S, all from Unicode 17.0.0.
Backslash escaping admits only CommonMark ASCII punctuation.

Entities use the complete 2,125 semicolon-terminated HTML5 named references,
including two-scalar replacements. Decimal references have 1–7 digits;
hexadecimal references have 1–6 digits and x/X. A semicolon is mandatory.
Zero, surrogates, and values above U+10FFFF become U+FFFD. Other admitted values,
including controls and noncharacters, are retained without browser Windows-1252
recovery. Unknown names, missing semicolons, excessive digits and malformed
references stay literal. Recognition happens once: decoded text is neither
recursively decoded nor reparsed as Markdown. Code and raw HTML remain literal.
Destinations, titles and fence information use their context-appropriate
CommonMark backslash unescaping/entity processing.

## Optional source spans

With positions false, every node lacks an own span slot. With positions true,
every node has an own span containing a fresh ordinary open `{ start, end }`
object, or null for a synthesized node with no physical source extent.
Coordinates are zero-based half-open original Unicode scalar offsets, never
UTF-8 byte offsets or UTF-16 code-unit offsets.

* Document spans `[0, source.size())`.
* Blocks span physical line extents excluding the final terminator; these spans
  may include outer container prefixes.
* Inline nodes span recognized syntax including their delimiters.
* Reference links/images span the use site, not the definition.
* Table rows span their physical lines. Explicit cell spans include untrimmed
  physical content between separators. Synthesized cells have null spans.
* Coalesced text spans the interval containing all contributing source extents.
* Soft breaks span the original terminator. Hard breaks span their marker and
  original terminator.

CRLF occupies two original positions. Expanded indentation maps to its original
tab. Span attribution survives trimming and noncontiguous container content;
no source map escapes in the AST.

## Resource limits and errors

The following limits are inclusive and independent:

| Resource | Maximum |
| --- | ---: |
| Original input scalars | 1048576 |
| Final AST nodes | 262144 |
| Final AST depth | 128 |
| Logical text scalars | 8388608 |

The document counts as one node at depth zero; depth counts parent-child edges.
Every actual final node counts, including synthesized cells. Logical text is the
sum of scalar lengths of every non-null value, info, destination and title slot
on all nodes. Repeated destinations count per occurrence even when storage is
shared. Kind discriminators, spans, alignments and other metadata do not count.
Accounting uses mathematical Integers without wrapping or truncation.

`MarkdownLimitError` is an ordinary Error prototype with immediate parent Error,
exported only by this module (see [ERRORS.md](ERRORS.md)). Any excess signals a
fresh occurrence. No partial document is returned. There is no truncation,
configurable budget, time limit, host-dependent limit or syntax substitution to
avoid a resource failure. Discarded provisional structures do not count. Early
rejection is allowed only when final excess is inevitable, including confirmed
table padding and repeated reference-destination amplification. Large inevitable
amplification must be rejected before materialization. Final structural limits
are verified without depending on source-dependent host recursion.

## Authority and compatibility boundary

Parsing acquires no Filesystem, Network, Process, FFI or external-execution
authority, spawns no Task or Actor, returns no Future, and introduces no shared
mutable accounting or parser instance. Ordinary cancellation and Error
propagation are preserved without retries or fallback parsers. JVM and Native
implementations obey the same contract.

This module parses semantic Markdown only. HTML rendering, tag filtering,
sanitization, URL authorization, streaming, CST, plugins, footnotes, Mermaid,
math, MDX and front matter are outside this contract. Raw HTML and potentially
dangerous destinations remain untrusted String data.
