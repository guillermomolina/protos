/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import java.util.List;
import java.util.Map;

/**
 * TEST009-T minimal single-line JSON encoder for compilerability causal-trace events.
 *
 * <p>Output is pure ASCII: every non-printable or non-ASCII character is written as a {@code
 * \\uXXXX} escape, so the event survives any platform stderr encoding and stays on one line.
 * Values are {@code null}, {@link String}, {@link Boolean}, integral {@link Number}s, {@link
 * List}s and insertion-ordered {@link Map}s with String keys; anything else is rejected rather
 * than rendered through {@code toString()}, which could leak object identities.
 */
final class ProtosCompilerabilityJson {
    private ProtosCompilerabilityJson() {}

    static String encode(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            writeString(out, string);
        } else if (value instanceof Boolean bool) {
            out.append(bool.booleanValue());
        } else if (value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte) {
            out.append(((Number) value).longValue());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object key is not a String");
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, key);
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    out.append(',');
                }
                write(out, list.get(index));
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException(
                    "unsupported JSON value type " + value.getClass().getName());
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
