# Strict Base64url Standard Library contract (D246-C)

Status: Normative. Language version: 0.1. Decision: D246-C. Implementation: I161.

## Ownership and module surface

`std:text/base64url` is a normal, independently importable Standard Library MODULE. It is not part of Core, Prelude, a generalized Base64 framework, or character encoding. Its public module object has exactly two own operations:

- `encode(bytes: Bytes) -> String`
- `decode(text: String) -> Bytes`

The functions run synchronously, create no Task or Future, require no host authority, and have no side effects on their inputs. They do not access the environment, system clock, network, filesystem, cryptography provider, or entropy source.

## Encoding

`encode` accepts only a genuine standard `Bytes` value and never implicitly encodes a String. It obtains a private, exact octet snapshot before processing the input: caller-created slots named `size`, `at`, `each`, or similar must not alter the bytes observed by the operation. It preserves every octet (including `0` and `255`) and does not retain or modify the source.

The result is an ordinary String using the exact RFC 4648 URL-safe alphabet `ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_`. Groups of three octets produce four characters; one or two remaining octets produce two or three characters. The encoder **never** emits `=` padding, line breaks, or separators. Encoding empty Bytes returns `""`.

## Strict decoding and canonicality

`decode` accepts only String input. Every character must belong to the URL-safe alphabet above: reject `+`, `/`, `=`, whitespace (including spaces, tabs, CR/LF), non-ASCII characters, control characters, and any other spelling. An encoded character count congruent to `1` modulo `4` is invalid.

The final sextet's unused low bits **must be zero**: for an ending of two characters the last four bits must be zero, and for an ending of three characters its last two bits must be zero. Thus `Zg` and `Zm8` are valid, while `Zh` and `Zm9` are invalid. No ignored, truncated, replaced, reinterpreted, or noncanonical form is accepted.

Each successful decoding produces a fresh, open, independent standard Bytes value containing precisely the decoded octets, including arbitrary binary data. Decoding empty input yields fresh empty Bytes. The decoder does not interpret bytes as UTF-8 or perform text normalization.

## Errors and resource behavior

Wrong input types and invalid encodings each signal a fresh ordinary `Error`. No standard Base64url-specific Error prototype is added. The reusable codec imposes no JWT-specific limit of 8192, 4096, or 1024 bytes; consumers such as JWT apply their own separate bounds before decoding or allocating untrusted payloads. Each operation is Actor-local and does not maintain cross-Actor state or retain borrowed mutable Bytes.
