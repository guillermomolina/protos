# Text styling and encodings: `std:text`

`std:text` groups two independent sets of modules:

- **styling** — `Style`, `StyledText`, `ANSI`, and `ColorMode` describe
  colored text and render it, without ever inspecting a terminal;
- **encoding helpers** — `UTF8`, `UTF16BE`, `UTF16LE`, and `Latin1` convert
  between Strings and Bytes and wrap byte streams as text streams.

The doc comments under [`protos/lib/text/`](../../../protos/lib/text/) are the
exact contract.

## Styling

| Module | Operations |
|---|---|
| `std:text/style` | `Style(foreground)`, `recognizes(value)` |
| `std:text/styled_text` | `StyledText(text)`, `StyledText(text, style)`, `concat(...fragments)`, `recognizes(value)` |
| `std:text/ansi` | `render(styledText, stylingEnabled)` |
| `std:text/color_mode` | `AUTO`, `ALWAYS`, `NEVER`, `recognizes(value)`, `resolve(mode, autoEnabled)` |

A foreground is exactly one of `"default"`, `"black"`, `"red"`, `"green"`,
`"yellow"`, `"blue"`, `"magenta"`, `"cyan"`, or `"white"`. Only foreground
colors exist; there is no bold, underline, background, or 256-color/RGB
support.

Styles have semantic equality; styled texts are opaque values compared by
identity, and no public operation reads their runs back.

`Style(...)` and `StyledText(...)` are ordinary invocations that reach each
module's source-defined `construct` method through the inherited `call` (see
[Construction: `init`, `construct`, and `call`](../03-closures-methods-and-receivers.md#construction-init-construct-and-call)).
The factory bodies are unchanged: each is still a Closure bound to its private
module facility, and the public invocation forms are the same.

```protos
Style: import("std:text/style")
StyledText: import("std:text/styled_text")
ANSI: import("std:text/ansi")

status: StyledText.concat("build ", StyledText("ok", Style("green")), "!")

ANSI.render(status, false) // "build ok!"
ANSI.render(status, true)  // "build \u{1B}[32mok\u{1B}[0m!"
```

With styling enabled, each non-default foreground starts with its SGR
sequence and `ESC[0m` is emitted when leaving it. Text is copied unchanged:
the renderer does not sanitize escape characters already present in your
Strings.

### Deciding whether to color

`ColorMode.resolve(mode, autoEnabled)` answers a Boolean: `ALWAYS` is `true`,
`NEVER` is `false`, and `AUTO` answers `autoEnabled`. Nothing in `std:text`
checks whether output is a terminal or reads environment variables such as
`NO_COLOR`; the program decides `autoEnabled` itself and passes the result on.

```protos
ColorMode: import("std:text/color_mode")

enabled: ColorMode.resolve(ColorMode.AUTO, false)
ANSI.render(status, enabled) // "build ok!"
```

Modes are their canonical Strings (`ColorMode.AUTO` is `"AUTO"`); no other
spelling or letter case is accepted. `std:logging/colored_formatter` uses the
same policy (see [Structured logging](logging.md)).

## Encoding helpers

Each of `std:text/utf8`, `std:text/utf16be`, `std:text/utf16le`, and
`std:text/latin1` has the same six operations over the corresponding Core
`Encoding`:

| Operation | Answers |
|---|---|
| `encode(text)` | Fresh Bytes |
| `decode(bytes)` | A String |
| `reader(source)` / `owningReader(source)` | A TextReader over a byte source |
| `writer(target)` / `owningWriter(target)` | A TextWriter over a byte target |

A *borrowing* reader or writer leaves the underlying byte stream open when it
is closed; an *owning* one closes it too. Chapter 11 explains the ownership
model in [Process, I/O, Filesystems, and Authority](../11-process-io-filesystems-and-authority.md).

```protos
UTF8: import("std:text/utf8")

bytes: UTF8.encode("héllo")
bytes.size()        // 6
UTF8.decode(bytes)  // "héllo"
```

Each helper delegates directly to the Core `Encoding`, `TextReader`, and
`TextWriter` operations, so argument validation and malformed or unmappable
input follow [`spec/io/TEXT_IO.md`](../../../spec/io/TEXT_IO.md). These modules are conveniences; `Encoding.UTF8.encode(text)`
and the Core `TextReader`/`TextWriter` remain available directly.

## Binary Base64url codec

`std:text/base64url` exports only `encode(bytes) -> String` and
`decode(text) -> Bytes`. This independent RFC 4648 URL-safe codec uses `-` and
`_`, emits no padding, and accepts only canonical unpadded input: whitespace,
`+`, `/`, `=`, Unicode, impossible lengths, and nonzero trailing unused pad
bits signal an ordinary fresh `Error`. Successful decoded results are fresh,
independent, open standard Bytes, including binary zero and 255. It imposes no
JWT-specific length cap.

```protos
Base64url: import("std:text/base64url")
bytes: Encoding.Latin1.encode("Hello")
wire: Base64url.encode(bytes) // "SGVsbG8"
copy: Base64url.decode(wire)
```

Base64url is **binary-to-ASCII encoding**, not Unicode text encoding. Never
assume that arbitrary decoded Bytes contain UTF-8 text: perform a separate
explicit UTF-8 decode only when the protocol requires valid UTF-8. JWT adds
separate protected-header/payload limits and authentication; see
[the strict JWT guide](jwt.md) and
[the normative codec contract](../../../spec/encoding/BASE64URL.md).

## JVM and Native

All `std:text` modules behave identically on both distributions. Neither
distribution decides terminal color support for you.
