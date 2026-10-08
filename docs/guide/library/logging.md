# Structured logging: `std:logging`

`std:logging` is explicit by design. There is no global logger, no registry,
no configuration file, and no ambient output. A logger is an ordinary frozen
value that you create and pass around, and it writes only to the sink you give
it.

| Module | Role |
|---|---|
| `std:logging/Level` | The five levels `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` and `compare` |
| `std:logging/Logger` | Creates loggers: level filtering, context fields, optional time source |
| `std:logging/LogEvent` | The immutable event a logger emits |
| `std:logging/MemorySink` | Keeps emitted events in memory (tests, self-inspection) |
| `std:logging/TextSink` | Writes each event as one line to a TextWriter you supply |
| `std:logging/TextFormatter` | Deterministic human-readable line |
| `std:logging/JsonFormatter` | Deterministic compact JSON object (uses `std:json`) |
| `std:logging/ColoredFormatter` | `TextFormatter` line with an ANSI-colored level |

The doc comments under
[`protos/lib/logging/`](../../../protos/lib/logging/) are the exact contract.

## Logging to standard output

A program gets its standard output only through its `process`. Pass the writer
to a `TextSink`, together with a formatter module:

```protos
ProcessStreams: import("std:io/ProcessStreams")
Level: import("std:logging/Level")
Logger: import("std:logging/Logger")
TextSink: import("std:logging/TextSink")
TextFormatter: import("std:logging/TextFormatter")

writer: ProcessStreams.stdoutWriter(process)
log: Logger(Level.INFO, TextSink(TextFormatter, writer))

log.info("started")
log.info("cache refreshed", %{ "entries": 42; "tags": ["hot", "warm"] })
log.debug("not written: below INFO")
```

Output:

```text
INFO "started"
INFO "cache refreshed" {"entries"=42, "tags"=["hot", "warm"]}
```

The sink borrows the writer: it never closes or flushes it. `emit` waits for
each write, so a slow writer suspends the logging Task; there is no background
queue.

## Logger operations

`Logger(minimumLevel, sink, timeSource = null)` answers a logger with:

- `isEnabled(level)`;
- `log(level, message)`, `log(level, message, fields)`, and
  `log(level, message, fields, error)`;
- `trace`, `debug`, `info`, `warn`, and `error`, taking the same arguments
  after the level;
- `with(fields)`, a derived logger whose context fields are merged into every
  event. Call-site fields override context fields.

```protos
requestLog: log.with(%{ "requestId": "r-7" })
requestLog.warn("slow response", %{ "ms": 1250 })
// WARN "slow response" {"ms"=1250, "requestId"="r-7"}
```

A call below the minimum level answers `null` after checking only its level; it
builds no event. Use `isEnabled(level)` to avoid computing expensive fields.

Fields are structured data only: `null`, Booleans, Strings, Integers, Floats,
Arrays, and Maps with String keys, nested to any depth. Any other value signals
an Error; the logger never converts values to Strings. Events snapshot their
fields, so later mutation of your Map does not change them.

An attached Error is kept by reference and rendered only as ` error=true`:

```protos
Failure: Error {}
Error.handle(() => { Failure().signal() }, (failure) => {
    log.error("request failed", %{ "requestId": "r-7" }, failure)
})
// ERROR "request failed" {"requestId"="r-7"} error=true
```

## Sink failures and time

- An Error signalled by a sink's `emit` is **contained** by the logging call:
  it does not reach the caller and is not reported elsewhere.
- Timestamps are opt-in. `timeSource` is a zero-argument callable answering a
  `std:datetime/Instant`; it is called once per enabled event. Without it,
  events have `timestamp` `null` and formatters omit the time. The library
  never reads a clock itself, and the Standard Library does not currently
  provide a wall-clock source; a program supplies one explicitly.

```protos
Instant: import("std:datetime/Instant")
fixed: Logger(Level.INFO, TextSink(TextFormatter, writer), () => { Instant(0) })
fixed.info("tick")
// 1970-01-01T00:00:00Z INFO "tick"
```

## Formatters

`TextFormatter.format(event)` writes one line:
`[TIMESTAMP ] LEVEL "message"[ {fields}][ error=true]`. Strings are quoted and
control characters escaped; Map keys are sorted by Unicode scalar order at
every depth, so output is deterministic.

`JsonFormatter.format(event)` writes one compact JSON object with members in
fixed order:

```text
{"timestamp":0,"level":"INFO","message":"tick","fields":{}}
```

`timestamp` is the Integer nanoseconds of the instant and is omitted when the
event has none. `"error":true` appears when an Error is attached. Float `NaN`
and infinities cannot be written as JSON and signal an Error.

`ColoredFormatter(mode, autoEnabled)` produces the `TextFormatter` line with the
level colored (TRACE blue, DEBUG cyan, INFO uncolored, WARN yellow, ERROR red).
The mode is a `std:text/ColorMode` value; the formatter never inspects the
terminal or environment, so the caller decides what `AUTO` means:

```protos
ColorMode: import("std:text/ColorMode")
ColoredFormatter: import("std:logging/ColoredFormatter")

colored: Logger(Level.INFO, TextSink(ColoredFormatter(ColorMode.ALWAYS, false), writer))
```

## Testing log output

`MemorySink()` keeps the exact events; `events()` answers a fresh frozen Array
of them in emission order:

```protos
MemorySink: import("std:logging/MemorySink")

sink: MemorySink()
Logger(Level.INFO, sink).info("cache refreshed", %{ "entries": 42 })
event: sink.events()[0]
event.level            // "INFO"
event.fields["entries"] // 42
```

## Limitations

- No global or default logger, no configuration files, and no `FATAL` level.
- No file, rotating, network, or asynchronous sink; write your own object with
  an `emit(event)` method when you need one.
- No source location (file/line) in events.
- No ordering guarantee between events emitted concurrently to a shared sink.

## JVM and Native

The library is ordinary Protos code; output is byte-identical on both
distributions.
