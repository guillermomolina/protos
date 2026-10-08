# Dates, times, and durations: `std:datetime`

`std:datetime` is a family of small, frozen value modules with exact Integer
arithmetic. It deliberately separates concepts that many libraries blur:

| Module | Represents | Has `compare` |
|---|---|---|
| `std:datetime/Date` | Civil date (proleptic Gregorian, years `-9999`..`9999`) | yes |
| `std:datetime/Time` | Time of day with nanoseconds, no leap seconds | yes |
| `std:datetime/LocalDateTime` | A Date plus a Time; not an instant | yes |
| `std:datetime/Instant` | Point on a uniform timeline, nanoseconds from `1970-01-01T00:00:00Z` | yes |
| `std:datetime/Offset` | Fixed offset in seconds, `-18:00:00`..`+18:00:00`; not a time zone | yes |
| `std:datetime/OffsetDateTime` | A LocalDateTime plus an Offset | no |
| `std:datetime/Duration` | Exact elapsed nanoseconds | yes |
| `std:datetime/Period` | Calendar amount: years, months, days | no |
| `std:datetime/ISO8601` | Bounded ISO 8601 text profile | — |
| `std:datetime/RFC3339` | Bounded RFC 3339 timestamp adapter | — |

Each source file's doc comment under
[`protos/lib/datetime/`](../../../protos/lib/datetime/) is the exact contract.

## What the library does not do

- **No clock.** Nothing in `std:datetime` reads the current time. A program
  that needs "now" receives it from something that has that authority; the
  logging library, for example, takes an explicit time-source callable.
- **No time zones.** There is no zone name, no time-zone database, and no
  daylight-saving rule. An `Offset` is only a fixed displacement.
- **No leap seconds**, no locale-dependent formatting, and no host time API.

## Constructing and comparing values

Each value module is called as a factory and answers a fresh frozen value.
Arguments are validated, never clamped or wrapped. The snippets on this page
build on each other's imports; comments show the answered value.

```protos
Date: import("std:datetime/Date")
Time: import("std:datetime/Time")
LocalDateTime: import("std:datetime/LocalDateTime")

release: Date(2026, 10, 8)
noon: Time(12, 0, 0, 0)                  // hour, minute, second, nanosecond
meeting: LocalDateTime(release, noon)

release.year                              // 2026
Date.compare(Date(2026, 1, 1), release)   // -1
Date(2026, 10, 8) == release              // true: semantic equality
Date(2026, 10, 8) === release             // false: distinct objects
```

`Date(2026, 2, 30)` and `Time(24, 0, 0, 0)` signal an Error. Every family has
`recognizes(value)` to test membership. Equal values have equal `hash`, so they
work as Map keys.

## Durations and instants

`Duration` is elapsed time; `Instant` is a timeline position. Both hold one
unbounded Integer `nanoseconds` slot.

```protos
Instant: import("std:datetime/Instant")
Duration: import("std:datetime/Duration")

epoch: Instant(0)
oneHour: Duration(3600 * 1000000000)
later: Instant.addDuration(epoch, oneHour)

Instant.durationBetween(epoch, later) == oneHour   // true
Duration.add(oneHour, Duration.negate(oneHour))    // Duration(0)
```

`Instant` also has `subtractDuration`; `Duration` has `subtract` and `negate`.
No operation applies a Duration to a Date, Time, or LocalDateTime: calendar
arithmetic is a Period's job.

## Calendar arithmetic with `Period`

A Period's years, months, and days are never normalized: twelve months is not
one year. Applying a period combines years and months, clamps the day once to
the end of the resulting month, then adds days.

```protos
Period: import("std:datetime/Period")

Period.addToDate(Period(0, 1, 0), Date(2026, 1, 31))   // Date(2026, 2, 28)
Period.subtractFromDate(Period(1, 0, 0), Date(2024, 2, 29)) // Date(2023, 2, 28)
Period(1, 0, 0) == Period(0, 12, 0)                     // false
```

`Period` also has `negate`, `addToLocalDateTime`, and
`subtractFromLocalDateTime` (the time of day is kept unchanged).

## Offsets and instants

An `OffsetDateTime` stores a local date-time and an offset exactly as given.
Converting to an instant is pure arithmetic:

```protos
Offset: import("std:datetime/Offset")
OffsetDateTime: import("std:datetime/OffsetDateTime")

plusTwo: Offset(2 * 3600)
local: OffsetDateTime(LocalDateTime(Date(2026, 10, 8), Time(12, 30, 0, 0)), plusTwo)
utc: OffsetDateTime.withOffsetSameInstant(local, Offset(0))   // 10:30 at offset 0

OffsetDateTime.sameInstant(local, utc)   // true
local == utc                              // false: equality is structural
```

`OffsetDateTime` also has `toInstant(value)` and `fromInstant(instant, offset)`.
It has no `compare`; compare instants instead.

## Text: `ISO8601` and `RFC3339`

`ISO8601` is a bounded canonical profile, not full ISO 8601: it accepts only
`YYYY-MM-DD`, `HH:MM:SS[.fraction]` (one to nine fraction digits), their
combination with an uppercase `T`, and an offset `Z`, `±HH:MM`, or
`±HH:MM:SS`. Years `-0001`..`-9999` are written with a leading `-`. Week dates,
ordinal dates, the basic format, comma fractions, and ISO durations are
rejected.

| `ISO8601` operation | Value |
|---|---|
| `parseDate` / `formatDate` | `Date` |
| `parseTime` / `formatTime` | `Time` |
| `parseLocalDateTime` / `formatLocalDateTime` | `LocalDateTime` |
| `parseOffsetDateTime` / `formatOffsetDateTime` | `OffsetDateTime` |
| `parseInstant` / `formatInstant` | `Instant` (always written in UTC with `Z`) |

`RFC3339` has `parseOffsetDateTime` and `formatOffsetDateTime` for strict
Internet timestamps: four-digit non-negative years, minute-resolution offsets,
uppercase `T` and `Z`. It rejects lowercase `t`/`z`, `:60`, and `-00:00`.

```protos
ISO8601: import("std:datetime/ISO8601")
RFC3339: import("std:datetime/RFC3339")

ISO8601.formatDate(Date(2026, 2, 28))                 // "2026-02-28"
ISO8601.formatInstant(Instant(0))                      // "1970-01-01T00:00:00Z"
ISO8601.formatTime(Time(9, 5, 0, 120000000))           // "09:05:00.12"

stamp: RFC3339.parseOffsetDateTime("2026-10-08T12:30:00+02:00")
ISO8601.formatInstant(OffsetDateTime.toInstant(stamp)) // "2026-10-08T10:30:00Z"
```

Parsing a formatted value answers an equal value. Formatting never rounds;
input with ten or more fraction digits is rejected rather than truncated.

## JVM and Native

The modules are pure Protos arithmetic and consult no host time, zone, or text
service, so results are identical on both distributions.
