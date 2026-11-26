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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosIndexedSnapshot;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosVectorValue;
import com.guillermomolina.protos.runtime.ProtosMatrixValue;
import java.util.ArrayList;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.List;

/**
 * Prepared iteration state of the structured each/match invocations over
 * local collections and scalar/numeric values shared by the structured
 * dispatcher and inline literal callback regions.
 */
final class ProtosStructuredEachCalls {
    private ProtosStructuredEachCalls() {}

    /**
     * PLAT044 B′ common loop state of a standard each whose callback a send
     * site may sequence locally (PERF026-D1 indexed, PERF026-D2 association,
     * PERF026-D3 Environment): validated receiver and callback, one snapshot
     * established before the first callback, one fresh
     * callback invocation per snapshot position carrying exactly that
     * position's arguments, advance only after that invocation completes
     * normally, and the original receiver as result. It lets a send site
     * sequence any such loop without duplicating its inline callback region;
     * each implementation keeps its own validation and snapshot authority.
     */
    sealed interface PreparedLocalEachCall
            permits PreparedIndexedEachCall,
                    PreparedAssociationEachCall,
                    PreparedEnvironmentEachCall {
        boolean hasNext();

        void advance();

        Object finish();

        /** The validated callback value supplied to this each invocation. */
        Object callback();

        /** The call of the callback for the current position. */
        ProtosCallRequest prepareCurrent();
    }

    /**
     * PLAT044 B′ (PERF026-D1) standard Array.each and Bytes.each loops, whose
     * observable shapes coincide: an ascending indexed snapshot and one
     * one-argument callback invocation per snapshot element.
     */
    sealed interface PreparedIndexedEachCall extends PreparedLocalEachCall
            permits PreparedArrayEachCall, PreparedBytesEachCall, PreparedValueEachCall {}

    /**
     * PLAT044 B′ (PERF026-D2) standard Map.each and IdentityMap.each loops,
     * whose observable shapes coincide: an insertion-ordered association
     * snapshot of representative keys and their values, and one
     * {@code block(key, value)} invocation per snapshot association. The two
     * receiver families stay distinct; only the loop view is shared.
     */
    sealed interface PreparedAssociationEachCall extends PreparedLocalEachCall
            permits PreparedMapEachCall, PreparedIdentityMapEachCall {}

    static final class PreparedArrayEachCall implements PreparedIndexedEachCall {
        private final ProtosArrayValue array;
        /* The published Array generation: known length, indexed reads, no copy. */
        private final ProtosIndexedSnapshot snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedArrayEachCall(
                Object receiver,
                Object[] supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosArrayValue value)
                    || supplied.length != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.array = value;
            this.block = supplied[0];
            ProtosStandardArrayProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.indexedGenerationSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.length();
        }

        @Override
        public ProtosCallRequest prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each callback requested after snapshot exhaustion");
            }
            return ProtosCallRequest.call(
                    block,
                    new Object[] {snapshot.at(index)},
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Array.each finished before snapshot exhaustion");
            }
            return array;
        }

        @Override
        public Object callback() {
            return block;
        }
    }


    /**
     * One retained cursor for immutable scalar text or a shallow numeric snapshot.
     * Preparation validates completely before observing storage; advancing belongs
     * to the structured loop after normal callback completion, including resumption.
     */
    static final class PreparedValueEachCall implements PreparedIndexedEachCall {
        private final Object receiver;
        private final Object block;
        private final ProtosActivation activation;
        private final String text;
        private final List<Object> snapshot;
        private int index;
        private int scalarEnd;

        private PreparedValueEachCall(
                Object receiver, Object block, ProtosActivation activation,
                String text, List<Object> snapshot) {
            this.receiver = receiver;
            this.block = block;
            this.activation = activation;
            this.text = text;
            this.snapshot = snapshot;
        }

        static PreparedValueEachCall forString(
                Object receiver, Object[] supplied, ProtosActivation activation) {
            if (!(receiver instanceof ProtosStringValue value)) {
                throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
            }
            Object block = validatedCallback(supplied, activation);
            return new PreparedValueEachCall(receiver, block, activation, value.value(), null);
        }

        static PreparedValueEachCall forVector(
                Object receiver, Object[] supplied, ProtosActivation activation) {
            if (!(receiver instanceof ProtosVectorValue value)) {
                throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
            }
            Object block = validatedCallback(supplied, activation);
            return new PreparedValueEachCall(
                    receiver, block, activation, null, numericSnapshot(value));
        }

        static PreparedValueEachCall forMatrix(
                Object receiver, Object[] supplied, ProtosActivation activation) {
            if (!(receiver instanceof ProtosMatrixValue value)) {
                throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
            }
            Object block = validatedCallback(supplied, activation);
            return new PreparedValueEachCall(
                    receiver, block, activation, null, numericSnapshot(value));
        }

        /** Copies storage once at entry; per-element snapshot reads remain compilable. */
        @TruffleBoundary
        private static List<Object> numericSnapshot(Object receiver) {
            return receiver instanceof ProtosVectorValue vector
                    ? vector.indexedSnapshot()
                    : ((ProtosMatrixValue) receiver).indexedSnapshot();
        }

        private static Object validatedCallback(Object[] supplied, ProtosActivation activation) {
            if (supplied.length != 1) {
                throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
            }
            Object block = supplied[0];
            ProtosStandardArrayProtocol.requireInvokableForStructured(block, activation);
            return block;
        }

        @Override
        public boolean hasNext() {
            return text != null ? index < text.length() : index < snapshot.size();
        }

        @Override
        public ProtosCallRequest prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException("value each callback requested after exhaustion");
            }
            Object element;
            if (text != null) {
                scalarEnd = index + Character.charCount(text.codePointAt(index));
                // Copy only this validated scalar's UTF-16 units, keeping general
                // substring range-diagnostic formatting out of compiled traversal.
                String scalar = scalarEnd == index + 1
                        ? String.valueOf(text.charAt(index))
                        : new String(new char[] {text.charAt(index), text.charAt(index + 1)});
                element = new ProtosStringValue(scalar);
            } else {
                element = snapshot.get(index);
            }
            return ProtosCallRequest.call(block, new Object[] {element}, activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException("value each cursor advanced after exhaustion");
            }
            index = text != null ? scalarEnd : index + 1;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException("value each finished before exhaustion");
            }
            return receiver;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    static final class PreparedArrayMatchCall {
        private final ProtosActivation activation;
        private final ProtosIndexedSnapshot matcherSnapshot;
        private final ProtosIndexedSnapshot subjectSnapshot;
        /* Allocated at the first consumed capture; a zero-capture match never allocates it. */
        private List<Object> captures;
        private int index;
        private boolean mismatch;

        PreparedArrayMatchCall(
                Object receiver,
                Object[] supplied,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosArrayValue matcher)
                    || supplied.length != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            Object subjectValue = supplied[0];
            if (!(subjectValue instanceof ProtosArrayValue subject)) {
                matcherSnapshot = ProtosIndexedSnapshot.elements(new Object[0], 0);
                subjectSnapshot = ProtosIndexedSnapshot.elements(new Object[0], 0);
                mismatch = true;
                return;
            }

            matcherSnapshot = matcher.indexedGenerationSnapshot();
            subjectSnapshot = subject.indexedGenerationSnapshot();

            if (matcherSnapshot.length() != subjectSnapshot.length()) {
                mismatch = true;
            }
        }

        boolean hasNext() {
            return !mismatch && index < matcherSnapshot.length();
        }

        ProtosCallRequest prepareCurrent() {
            return ProtosCallRequest.send(
                    currentMatcher(),
                    "match",
                    activation,
                    new Object[] {currentSubject()});
        }

        /*
         * The current child matcher, read from the matcher snapshot; the
         * lowering sends it {@code match} through the ordinary specialized
         * invocation operations.
         */
        Object currentMatcher() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.match child requested after terminal outcome");
            }
            return matcherSnapshot.at(index);
        }

        /* The subject element at the current index, from the subject snapshot. */
        Object currentSubject() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.match child requested after terminal outcome");
            }
            return subjectSnapshot.at(index);
        }

        void acceptCurrent(Object outcome) {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.match child outcome accepted after terminal outcome");
            }

            if (outcome == Boolean.FALSE) {
                mismatch = true;
                return;
            }

            if (outcome == Boolean.TRUE) {
                index++;
                return;
            }

            if (outcome instanceof ProtosArrayValue childCaptures) {
                ProtosIndexedSnapshot observedCaptures =
                        childCaptures.indexedGenerationSnapshot();

                if (observedCaptures.length() == 0) {
                    throw ProtosCoreErrors.signal(
                            activation,
                            ProtosCoreErrors.newError(activation));
                }

                for (int captureIndex = 0;
                        captureIndex < observedCaptures.length();
                        captureIndex++) {
                    if (captures == null) {
                        captures = new ArrayList<>();
                    }
                    captures.add(observedCaptures.at(captureIndex));
                }
                index++;
                return;
            }

            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Array.match finished before snapshot exhaustion");
            }

            if (mismatch) {
                return Boolean.FALSE;
            }

            if (captures == null || captures.isEmpty()) {
                return Boolean.TRUE;
            }

            return activation
                    .prelude()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "standard Array.match requires an owning Core prelude"))
                    .newArray(captures);
        }
    }

    static final class PreparedBytesEachCall implements PreparedIndexedEachCall {
        private final ProtosBytesValue bytes;
        private final byte[] snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedBytesEachCall(
                Object receiver,
                Object[] supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosBytesValue value)
                    || supplied.length != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.bytes = value;
            this.block = supplied[0];
            ProtosStandardBytesProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.octetSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.length;
        }

        @Override
        public ProtosCallRequest prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each callback requested after snapshot exhaustion");
            }
            return ProtosCallRequest.call(
                    block,
                    new Object[] {ProtosNumericValueSupport.octet(snapshot[index] & 0xff)},
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each finished before snapshot exhaustion");
            }
            return bytes;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    /**
     * Standard Environment.each loop state. It joins the PLAT044 B′
     * (PERF026-D3) local each view directly, not as an association each: its
     * snapshot is not a shallow association snapshot but the complete
     * portable (String, String) snapshot, converted, validated and
     * canonically ordered by the Environment protocol before any callback is
     * prepared.
     */
    static final class PreparedEnvironmentEachCall implements PreparedLocalEachCall {
        private final ProtosEnvironmentValue environment;
        /* Interleaved (name, value) pairs of the converted portable snapshot. */
        private final ProtosIndexedSnapshot snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedEnvironmentEachCall(
                Object receiver,
                Object[] supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosEnvironmentValue value)
                    || supplied.length != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.environment = value;
            this.block = supplied[0];
            ProtosStandardEnvironmentProtocol.requireInvokableForStructured(
                    block,
                    activation);
            /*
             * PLAT028 Environment.each must preserve the historical eager
             * portable-representation cutover. This call validates/converts
             * the complete environment before callback #1 can be prepared.
             */
            this.snapshot = portableSnapshot(value, activation);
        }

        /*
         * The process environment is host data: reading, converting and
         * ordering it is the existing Environment host operation, after which
         * the loop reads only the indexed pairs.
         */
        @TruffleBoundary
        private static ProtosIndexedSnapshot portableSnapshot(
                ProtosEnvironmentValue value, ProtosActivation activation) {
            List<ProtosEnvironmentValue.PortableEntry> entries =
                    ProtosStandardEnvironmentProtocol.portableEntriesForStructured(
                            value,
                            activation);
            int count = entries.size();
            Object[] namesAndValues = new Object[count * 2];
            for (int index = 0; index < count; index++) {
                ProtosEnvironmentValue.PortableEntry entry = entries.get(index);
                namesAndValues[index * 2] = entry.name();
                namesAndValues[index * 2 + 1] = entry.value();
            }
            return ProtosIndexedSnapshot.associations(namesAndValues, count);
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.length();
        }

        @Override
        public ProtosCallRequest prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Environment.each callback requested after snapshot exhaustion");
            }
            return ProtosCallRequest.call(
                    block,
                    new Object[] {snapshot.keyAt(index), snapshot.valueAt(index)},
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Environment.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Environment.each finished before snapshot exhaustion");
            }
            return environment;
        }

        @Override
        public Object callback() {
            return block;
        }
    }


    static final class PreparedIdentityMapAtIfAbsentCall {
        private final ProtosIdentityMapValue.Entry match;
        private final Object fallback;
        private final ProtosActivation activation;
        private boolean fallbackPrepared;

        PreparedIdentityMapAtIfAbsentCall(
                Object receiver,
                Object[] supplied,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosIdentityMapValue map)
                    || supplied.length != 2) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            Object key = supplied[0];
            this.fallback = supplied[1];

            this.match =
                    ProtosStandardIdentityMapProtocol.findForStructured(
                            map,
                            key);
        }

        boolean needsFallback() {
            return match == null;
        }

        ProtosCallRequest prepareFallback() {
            if (!needsFallback() || fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent fallback requested in an invalid state");
            }

            fallbackPrepared = true;

            return ProtosCallRequest.call(
                    fallback,
                    new Object[0],
                    activation);
        }

        Object finishFallback(Object result) {
            if (!needsFallback() || !fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent fallback result accepted in an invalid state");
            }
            return result;
        }

        Object finishPresent() {
            if (needsFallback()) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent present path has no matching entry");
            }
            if (fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent present path prepared its fallback");
            }
            return match.value();
        }
    }


    static final class PreparedIdentityMapEachCall implements PreparedAssociationEachCall {
        private final ProtosIdentityMapValue identityMap;
        private final ProtosIndexedSnapshot snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedIdentityMapEachCall(
                Object receiver,
                Object[] supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosIdentityMapValue value)
                    || supplied.length != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.identityMap = value;
            this.block = supplied[0];
            ProtosStandardIdentityMapProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.associationIndexedSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.length();
        }

        @Override
        public ProtosCallRequest prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each callback requested after snapshot exhaustion");
            }
            return ProtosCallRequest.call(
                    block,
                    new Object[] {snapshot.keyAt(index), snapshot.valueAt(index)},
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each finished before snapshot exhaustion");
            }
            return identityMap;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    static final class PreparedMapEachCall implements PreparedAssociationEachCall {
        private final ProtosMapValue map;
        private final ProtosIndexedSnapshot snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedMapEachCall(
                Object receiver,
                Object[] supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosMapValue value)
                    || supplied.length != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = value;
            this.block = supplied[0];
            ProtosStandardMapProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.associationIndexedSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.length();
        }

        @Override
        public ProtosCallRequest prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Map.each callback requested after snapshot exhaustion");
            }
            return ProtosCallRequest.call(
                    block,
                    new Object[] {snapshot.keyAt(index), snapshot.valueAt(index)},
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Map.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Map.each finished before snapshot exhaustion");
            }
            return map;
        }

        @Override
        public Object callback() {
            return block;
        }
    }
}
