# Retained Markdown upstream inputs

The following original data inputs are retained locally:

| Local path | Exact source |
| --- | --- |
| `commonmark-0.31.2/spec.json` | https://spec.commonmark.org/0.31.2/spec.json |
| `gfm-0.29/spec.html` | https://github.github.com/gfm/ (published GFM 0.29 snapshot) |
| `unicode-17.0.0/UnicodeData.txt` | https://www.unicode.org/Public/17.0.0/ucd/UnicodeData.txt |
| `unicode-17.0.0/CaseFolding.txt` | https://www.unicode.org/Public/17.0.0/ucd/CaseFolding.txt |
| `whatwg/entities.json` | https://html.spec.whatwg.org/entities.json |
| `cmark-0.31.2/entities.inc` | cmark commit `eec0eeba6d31189fd828314576494566d539b1e3`, `src/entities.inc`, blob `e80d9aa9a4f83e2a062c06ed7a3075c031794b4e` |

Retain original licenses alongside inputs as `commonmark-0.31.2/LICENSE`,
`gfm-0.29/LICENSE`, `unicode-17.0.0/LICENSE`, `whatwg/LICENSE`, and
`cmark-0.31.2/COPYING`. CommonMark and GFM examples remain CC BY-SA 4.0;
Unicode data retains Unicode License V3; WHATWG and cmark retain their actual
upstream notices. Do not replace upstream notices with the Protos APL notice.
The retained input and license byte identities are recorded in
`PROVENANCE.json` and `SHA256SUMS`.

The offline generator `tools/markdown_data_projection.py` creates three private
Protos data sources, all 652 CommonMark examples, the 27 published GFM extension
examples (198–205, 279–280, 491–493, 622–635), exhaustive 2,125 named-entity
cases, manifest entries, `PROVENANCE.json` and `SHA256SUMS`. It requires matching
WHATWG and independently decoded pinned cmark mappings before writing anything.
Generated fixture sources preserve upstream attribution. `Harness.protos` is
hand-written Protos-owned support, never an executable manifest entry.

The generated inventory contains 652 CommonMark examples, 27 selected GFM
examples and 2,125 named-entity cases. Generation is not executable
conformance evidence. Any actual compatibility difference must retain the
original fixture and document the individual reason, rather than use a
blanket exclusion.
