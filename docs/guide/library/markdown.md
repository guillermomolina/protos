# Markdown semantic parsing

`std:markdown` parses Markdown into ordinary mutable Protos objects and Arrays.
The normative owner is [MARKDOWN.md](../../../spec/semantics/MARKDOWN.md).

```protos
Markdown: import("std:markdown")
document: Markdown.parse("# Hello")
```

```protos
document: Markdown.parse(
    source,
    profile: "commonmark-0.31.2-gfm-extensions",
    positions: true
)
```

The default profile is exactly `commonmark-0.31.2`. The other accepted profile is
exactly `commonmark-0.31.2-gfm-extensions`: CommonMark 0.31.2 plus published GFM
0.29 tables, task items, strikethrough and extended autolinks. CommonMark rules
prevail for shared syntax. This does not promise complete GitHub.com behavior.
All arguments use ordinary Closure association; explicit null is not a default.
Source/profile require genuine Strings and positions requires canonical Boolean.

Every node owns `kind`, plus the following slots. Containers always own a fresh
open children Array; leaves lack children. Adjacent text coalesces and empty text
is omitted. Results have no parent links, source maps or custom equality.

| kind | Other slots |
| --- | --- |
| document, paragraph, blockquote, emphasis, strong, strikethrough | children |
| heading | level, children |
| thematicBreak, softBreak, hardBreak | none |
| list | ordered, start, tight, children |
| listItem | task, children |
| codeBlock | value, info |
| htmlBlock, text, codeSpan, htmlInline | value |
| link, image | destination, title, children |
| table | alignments, children |
| tableRow, tableCell | children |

Unordered list start is null; ordered start preserves the marker Integer,
including zero. Tightness describes blank-line relationships. Every item has
null task ordinarily, false for incomplete GFM tasks and true for completed
ones. Isolated markers remain literal. Heading levels are 1–6. Fenced code info
is the complete processed String (empty when absent); indented code info is
null. An absent link title is null, while an explicitly empty title is `""`.
Table alignments are left/center/right Strings or null; missing cells have empty
children and excess cells are discarded. The first row is the header.

References resolve after definitions across the document have been collected.
The first equivalent definition wins. Labels use CommonMark whitespace rules
and Unicode 17 full default case folding, without NFC or general entity decoding.
Entities are decoded once where CommonMark permits; code and HTML remain literal.
CR/LF/CRLF have CommonMark line semantics, NUL becomes U+FFFD and BOM survives.

With positions true, each node also owns a fresh `{ start, end }` span, or null
for a synthesized node. Offsets are half-open original Unicode scalar positions:
a supplementary character counts once and CRLF twice. Inline spans include
syntax delimiters; reference spans describe the use site. With positions false,
no node owns span. Mutating a result does not affect another parse invocation.

Limits are inclusive: 1,048,576 original input scalars, 262,144 final nodes,
depth 128 (document at zero), and 8,388,608 logical text scalars. Logical text
counts every non-null value, info, destination and title, including repeated
reference destinations. Excess signals a fresh `MarkdownLimitError` occurrence;
invalid arguments signal ordinary Error. Parsing does not truncate or return a
partial tree.

Parsing acquires no external authority and returns synchronously. Raw HTML and
URI destinations remain untrusted data, including dangerous-looking schemes.
Safe HTML rendering requires separate escaping, sanitization and URL policy.
No production HTML renderer is provided by this module or its example.

See [semantic-tree.protos](../../../protos/examples/markdown/semantic-tree.protos).
