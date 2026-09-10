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
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded, non-evaluating CLI/REPL diagnostic value inspector.
 *
 * <p>D063 keeps this surface intentionally separate from ordinary program {@code print(...)}
 * output and from serialization. The inspector reads only already-materialized value state and
 * never invokes guest methods in order to produce a representation.
 */
final class ProtosDiagnosticInspector {
    static final int MAX_DEPTH = 6;
    static final int MAX_ITEMS = 24;
    static final int MAX_STRING_CODE_POINTS = 256;
    static final int MAX_OUTPUT_CHARS = 4096;

    String render(Object value) {
        RenderState state = new RenderState();
        appendValue(value, state, new IdentityHashMap<>(), 0);
        return state.finish();
    }

    private void appendValue(
            Object value,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth) {
        if (state.full()) return;

        if (value == ProtosNullValue.INSTANCE) {
            state.append("null");
            return;
        }
        if (value == ProtosBooleanValue.TRUE) {
            state.append("true");
            return;
        }
        if (value == ProtosBooleanValue.FALSE) {
            state.append("false");
            return;
        }
        if (value instanceof ProtosIntegerValue integer) {
            state.append(integer.value().toString());
            return;
        }
        if (value instanceof ProtosFloatValue floating) {
            state.append(Double.toString(floating.value()));
            return;
        }
        if (value instanceof ProtosFixedIntegerValue integer) {
            state.append(integer.value().toString());
            return;
        }
        if (value instanceof ProtosStringValue string) {
            appendString(string.value(), state);
            return;
        }

        if (value instanceof ProtosClosureValue) {
            state.append("<closure>");
            return;
        }
        if (value instanceof ProtosFutureValue) {
            state.append("<future>");
            return;
        }

        if (depth >= MAX_DEPTH) {
            state.append("...");
            return;
        }

        if (path.put(value, Boolean.TRUE) != null) {
            state.append("<cycle>");
            return;
        }

        try {
            if (value instanceof ProtosArrayValue array) {
                appendArray(array.indexedSnapshot(), state, path, depth);
                return;
            }
            if (value instanceof ProtosMapValue map) {
                var entries = map.keyedSnapshot();
                state.append("{");
                int index = 0;
                for (var entry : entries) {
                    if (index >= MAX_ITEMS || state.full()) break;
                    if (index > 0) state.append(", ");
                    appendValue(entry.key(), state, path, depth + 1);
                    state.append(": ");
                    appendValue(entry.value(), state, path, depth + 1);
                    index++;
                }
                if (entries.size() > MAX_ITEMS && !state.full()) {
                    if (index > 0) state.append(", ");
                    state.append("...");
                }
                state.append("}");
                return;
            }
            if (value instanceof ProtosIdentityMapValue map) {
                var entries = map.keyedSnapshot();
                state.append("IdentityMap{");
                int index = 0;
                for (var entry : entries) {
                    if (index >= MAX_ITEMS || state.full()) break;
                    if (index > 0) state.append(", ");
                    appendValue(entry.key(), state, path, depth + 1);
                    state.append(": ");
                    appendValue(entry.value(), state, path, depth + 1);
                    index++;
                }
                if (entries.size() > MAX_ITEMS && !state.full()) {
                    if (index > 0) state.append(", ");
                    state.append("...");
                }
                state.append("}");
                return;
            }
            if (value instanceof ProtosObjectValue object
                    && value.getClass() == ProtosObjectValue.class) {
                appendObject(object.localSlotsSnapshot(), state, path, depth);
                return;
            }

            /*
             * Specialized/capability-backed object families stay opaque until their safe
             * diagnostic-family projection is implemented in the next bounded CLI008-B slice.
             * Never fall back to Java class names or host toString().
             */
            state.append("<value>");
        } finally {
            path.remove(value);
        }
    }

    private void appendArray(
            List<Object> elements,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth) {
        state.append("[");
        int limit = Math.min(elements.size(), MAX_ITEMS);
        for (int i = 0; i < limit && !state.full(); i++) {
            if (i > 0) state.append(", ");
            appendValue(elements.get(i), state, path, depth + 1);
        }
        if (elements.size() > MAX_ITEMS && !state.full()) {
            if (limit > 0) state.append(", ");
            state.append("...");
        }
        state.append("]");
    }

    private void appendObject(
            Map<String, Object> slots,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth) {
        state.append("Object {");
        int index = 0;
        for (Map.Entry<String, Object> entry : slots.entrySet()) {
            if (index >= MAX_ITEMS || state.full()) break;
            if (index > 0) state.append(", ");
            appendSlotName(entry.getKey(), state);
            state.append(": ");
            appendValue(entry.getValue(), state, path, depth + 1);
            index++;
        }
        if (slots.size() > MAX_ITEMS && !state.full()) {
            if (index > 0) state.append(", ");
            state.append("...");
        }
        state.append("}");
    }

    private void appendSlotName(String name, RenderState state) {
        if (isSimpleSlotName(name)) {
            state.append(name);
        } else {
            appendString(name, state);
        }
    }

    private static boolean isSimpleSlotName(String name) {
        if (name.isEmpty()) return false;
        char first = name.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }

    private void appendString(String value, RenderState state) {
        state.append("\"");
        int codePointCount = value.codePointCount(0, value.length());
        int visible = Math.min(codePointCount, MAX_STRING_CODE_POINTS);
        int offset = 0;
        for (int i = 0; i < visible && !state.full(); i++) {
            int codePoint = value.codePointAt(offset);
            appendEscapedCodePoint(codePoint, state);
            offset += Character.charCount(codePoint);
        }
        if (codePointCount > MAX_STRING_CODE_POINTS && !state.full()) {
            state.append("...");
        }
        state.append("\"");
    }

    private void appendEscapedCodePoint(int codePoint, RenderState state) {
        switch (codePoint) {
            case '\\' -> state.append("\\\\");
            case '"' -> state.append("\\\"");
            case '\n' -> state.append("\\n");
            case '\r' -> state.append("\\r");
            case '\t' -> state.append("\\t");
            case '\b' -> state.append("\\b");
            case '\f' -> state.append("\\f");
            default -> {
                if (codePoint < 0x20 || codePoint == 0x7f) {
                    state.append(String.format("\\u%04X", codePoint));
                } else {
                    state.appendCodePoint(codePoint);
                }
            }
        }
    }

    private static final class RenderState {
        private final StringBuilder out = new StringBuilder();
        private boolean truncated;

        boolean full() {
            return out.length() >= MAX_OUTPUT_CHARS;
        }

        void append(String text) {
            if (text.isEmpty() || full()) {
                if (!text.isEmpty()) truncated = true;
                return;
            }
            int remaining = MAX_OUTPUT_CHARS - out.length();
            if (text.length() <= remaining) {
                out.append(text);
                return;
            }
            out.append(text, 0, remaining);
            truncated = true;
        }

        void appendCodePoint(int codePoint) {
            append(new String(Character.toChars(codePoint)));
        }

        String finish() {
            if (!truncated) return out.toString();
            if (out.length() >= 3) {
                out.replace(out.length() - 3, out.length(), "...");
            }
            return out.toString();
        }
    }
}
