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

import com.guillermomolina.protos.runtime.ProtosActorRefValue;
import com.guillermomolina.protos.runtime.ProtosByteRegionValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFileValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosGroupRefValue;
import com.guillermomolina.protos.runtime.ProtosGroupSendOperationValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import com.guillermomolina.protos.runtime.ProtosProcessArgumentsValue;
import com.guillermomolina.protos.runtime.ProtosProcessCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSendOperationValue;
import com.guillermomolina.protos.runtime.ProtosTcpConnectionValue;
import com.guillermomolina.protos.runtime.ProtosTcpListenerValue;
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
    // Evolvable CLI presentation policy under D063; not a print/serialization contract.
    static final int MAX_COMPACT_CHARS = 96;
    private static final int INDENT_SPACES = 2;

    private enum Layout {
        COMPACT,
        MULTILINE
    }

    String render(Object value) {
        RenderState compact = new RenderState();
        appendValue(value, compact, new IdentityHashMap<>(), 0, Layout.COMPACT);
        String oneLine = compact.finish();
        if (!isStructuredValue(value) || oneLine.length() <= MAX_COMPACT_CHARS) {
            return oneLine;
        }

        RenderState pretty = new RenderState();
        appendValue(value, pretty, new IdentityHashMap<>(), 0, Layout.MULTILINE);
        return pretty.finish();
    }

    private static boolean isStructuredValue(Object value) {
        return value instanceof ProtosBytesValue
                || value instanceof ProtosByteRegionValue
                || value instanceof ProtosProcessArgumentsValue
                || value instanceof ProtosArrayValue
                || value instanceof ProtosMapValue
                || value instanceof ProtosIdentityMapValue
                || (value instanceof ProtosObjectValue && value.getClass() == ProtosObjectValue.class);
    }

    private void appendValue(
            Object value,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth,
            Layout layout) {
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
        if (value instanceof ProtosActorRefValue) {
            state.append("<actor-ref>");
            return;
        }
        if (value instanceof ProtosGroupRefValue) {
            state.append("<group-ref>");
            return;
        }
        if (value instanceof ProtosSendOperationValue) {
            state.append("<send-operation>");
            return;
        }
        if (value instanceof ProtosGroupSendOperationValue) {
            state.append("<group-send-operation>");
            return;
        }
        if (value instanceof ProtosEncodingValue) {
            state.append("<encoding>");
            return;
        }
        if (value instanceof ProtosEnvironmentValue) {
            state.append("<environment>");
            return;
        }
        if (value instanceof ProtosPathValue) {
            state.append("<path>");
            return;
        }
        if (value instanceof ProtosProcessCapabilityValue) {
            state.append("<process>");
            return;
        }
        if (value instanceof ProtosProcessStandardStreamValue) {
            state.append("<process-stream>");
            return;
        }
        if (value instanceof ProtosFilesystemValue) {
            state.append("<filesystem>");
            return;
        }
        if (value instanceof ProtosFileValue) {
            state.append("<file>");
            return;
        }
        if (value instanceof ProtosNetworkCapabilityValue) {
            state.append("<network>");
            return;
        }
        if (value instanceof ProtosTcpConnectionValue) {
            state.append("<tcp-connection>");
            return;
        }
        if (value instanceof ProtosTcpListenerValue) {
            state.append("<tcp-listener>");
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
            if (value instanceof ProtosBytesValue bytes) {
                appendNamedSequence("Bytes", bytes.indexedSnapshot(), state, path, depth, layout);
                return;
            }
            if (value instanceof ProtosByteRegionValue bytes) {
                appendNamedSequence("ByteRegion", bytes.indexedSnapshot(), state, path, depth, layout);
                return;
            }
            if (value instanceof ProtosProcessArgumentsValue arguments) {
                appendNamedSequence(
                        "ProcessArguments",
                        arguments.valuesForRuntime(),
                        state,
                        path,
                        depth,
                        layout);
                return;
            }
            if (value instanceof ProtosArrayValue array) {
                appendArray(array.indexedSnapshot(), state, path, depth, layout);
                return;
            }
            if (value instanceof ProtosMapValue map) {
                appendMap(map.keyedSnapshot(), state, path, depth, layout);
                return;
            }
            if (value instanceof ProtosIdentityMapValue map) {
                appendIdentityMap(map.keyedSnapshot(), state, path, depth, layout);
                return;
            }
            if (value instanceof ProtosObjectValue object
                    && value.getClass() == ProtosObjectValue.class) {
                appendObject(object.localSlotsSnapshot(), state, path, depth, layout);
                return;
            }

            /*
             * Unknown host-represented families remain opaque rather than falling back to Java
             * class names, host toString(), or implementation-specific identity.
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
            int depth,
            Layout layout) {
        appendSequence(null, elements, state, path, depth, layout);
    }

    private void appendNamedSequence(
            String family,
            List<?> elements,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth,
            Layout layout) {
        appendSequence(family, elements, state, path, depth, layout);
    }

    private void appendSequence(
            String family,
            List<?> elements,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth,
            Layout layout) {
        if (family != null) state.append(family);
        state.append("[");
        int limit = Math.min(elements.size(), MAX_ITEMS);
        boolean truncatedItems = elements.size() > MAX_ITEMS;

        if (layout == Layout.COMPACT || (limit == 0 && !truncatedItems)) {
            for (int i = 0; i < limit && !state.full(); i++) {
                if (i > 0) state.append(", ");
                appendValue(elements.get(i), state, path, depth + 1, layout);
            }
            if (truncatedItems && !state.full()) {
                if (limit > 0) state.append(", ");
                state.append("...");
            }
            state.append("]");
            return;
        }

        state.append("\n");
        for (int i = 0; i < limit && !state.full(); i++) {
            appendIndent(state, depth + 1);
            appendValue(elements.get(i), state, path, depth + 1, layout);
            if (i + 1 < limit || truncatedItems) state.append(",");
            state.append("\n");
        }
        if (truncatedItems && !state.full()) {
            appendIndent(state, depth + 1);
            state.append("...\n");
        }
        appendIndent(state, depth);
        state.append("]");
    }

    private void appendMap(
            List<ProtosMapValue.Entry> entries,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth,
            Layout layout) {
        state.append("{");
        int limit = Math.min(entries.size(), MAX_ITEMS);
        boolean truncatedItems = entries.size() > MAX_ITEMS;

        if (layout == Layout.COMPACT || (limit == 0 && !truncatedItems)) {
            for (int i = 0; i < limit && !state.full(); i++) {
                if (i > 0) state.append(", ");
                var entry = entries.get(i);
                appendValue(entry.key(), state, path, depth + 1, layout);
                state.append(": ");
                appendValue(entry.value(), state, path, depth + 1, layout);
            }
            if (truncatedItems && !state.full()) {
                if (limit > 0) state.append(", ");
                state.append("...");
            }
            state.append("}");
            return;
        }

        state.append("\n");
        for (int i = 0; i < limit && !state.full(); i++) {
            var entry = entries.get(i);
            appendIndent(state, depth + 1);
            appendValue(entry.key(), state, path, depth + 1, layout);
            state.append(": ");
            appendValue(entry.value(), state, path, depth + 1, layout);
            if (i + 1 < limit || truncatedItems) state.append(",");
            state.append("\n");
        }
        if (truncatedItems && !state.full()) {
            appendIndent(state, depth + 1);
            state.append("...\n");
        }
        appendIndent(state, depth);
        state.append("}");
    }

    private void appendIdentityMap(
            List<ProtosIdentityMapValue.Entry> entries,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth,
            Layout layout) {
        state.append("IdentityMap{");
        int limit = Math.min(entries.size(), MAX_ITEMS);
        boolean truncatedItems = entries.size() > MAX_ITEMS;

        if (layout == Layout.COMPACT || (limit == 0 && !truncatedItems)) {
            for (int i = 0; i < limit && !state.full(); i++) {
                if (i > 0) state.append(", ");
                var entry = entries.get(i);
                appendValue(entry.key(), state, path, depth + 1, layout);
                state.append(": ");
                appendValue(entry.value(), state, path, depth + 1, layout);
            }
            if (truncatedItems && !state.full()) {
                if (limit > 0) state.append(", ");
                state.append("...");
            }
            state.append("}");
            return;
        }

        state.append("\n");
        for (int i = 0; i < limit && !state.full(); i++) {
            var entry = entries.get(i);
            appendIndent(state, depth + 1);
            appendValue(entry.key(), state, path, depth + 1, layout);
            state.append(": ");
            appendValue(entry.value(), state, path, depth + 1, layout);
            if (i + 1 < limit || truncatedItems) state.append(",");
            state.append("\n");
        }
        if (truncatedItems && !state.full()) {
            appendIndent(state, depth + 1);
            state.append("...\n");
        }
        appendIndent(state, depth);
        state.append("}");
    }

    private void appendObject(
            Map<String, Object> slots,
            RenderState state,
            IdentityHashMap<Object, Boolean> path,
            int depth,
            Layout layout) {
        state.append("Object {");
        int limit = Math.min(slots.size(), MAX_ITEMS);
        boolean truncatedItems = slots.size() > MAX_ITEMS;

        if (layout == Layout.COMPACT || (limit == 0 && !truncatedItems)) {
            int index = 0;
            for (Map.Entry<String, Object> entry : slots.entrySet()) {
                if (index >= limit || state.full()) break;
                if (index > 0) state.append(", ");
                appendSlotName(entry.getKey(), state);
                state.append(": ");
                appendValue(entry.getValue(), state, path, depth + 1, layout);
                index++;
            }
            if (truncatedItems && !state.full()) {
                if (limit > 0) state.append(", ");
                state.append("...");
            }
            state.append("}");
            return;
        }

        state.append("\n");
        int index = 0;
        for (Map.Entry<String, Object> entry : slots.entrySet()) {
            if (index >= limit || state.full()) break;
            appendIndent(state, depth + 1);
            appendSlotName(entry.getKey(), state);
            state.append(": ");
            appendValue(entry.getValue(), state, path, depth + 1, layout);
            if (index + 1 < limit || truncatedItems) state.append(",");
            state.append("\n");
            index++;
        }
        if (truncatedItems && !state.full()) {
            appendIndent(state, depth + 1);
            state.append("...\n");
        }
        appendIndent(state, depth);
        state.append("}");
    }

    private static void appendIndent(RenderState state, int depth) {
        state.append(" ".repeat(Math.max(0, depth * INDENT_SPACES)));
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
