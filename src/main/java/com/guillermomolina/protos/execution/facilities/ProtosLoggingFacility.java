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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Private non-dispatch event-recognition boundary for {@code std:logging/log_event}.
 * Candidate delegation, mutation state, slots and collection storage are inspected directly
 * without invoking candidate behavior. Event construction and logging policy live in Protos.
 */
public final class ProtosLoggingFacility {
    public static final ProtosModuleKey EVENT_MODULE_KEY =
            ProtosModuleKey.module("std:logging/log_event");
    public static final String EVENT_BOOTSTRAP_SLOT = "_logEventFacility";
    private static final Set<String> EVENT_SLOTS =
            Set.of("level", "message", "fields", "error", "timestamp");
    private static final Set<String> INSTANT_SLOTS = Set.of("nanoseconds", "equals", "==", "hash");
    private static final Set<String> LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    private ProtosLoggingFacility() {}

    /** Creates the frozen, stateless event-recognition facility of {@code std:logging/log_event}. */
    public static ProtosObjectValue createEventFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facility.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> recognizes(activation, supplied)));
        facility.createLocalSlot(
                "isAttachableError",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> isAttachableError(activation, supplied)));
        return facility.freeze();
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object recognizes(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 3
                || !(supplied.get(1) instanceof ProtosObjectValue prototype)
                || !(supplied.get(2) instanceof ProtosObjectValue instantPrototype)) {
            throw invalid(activation);
        }
        return Boolean.valueOf(
                recognizesEvent(activation, supplied.get(0), prototype, instantPrototype));
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object isAttachableError(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1) {
            throw invalid(activation);
        }
        return Boolean.valueOf(attachableError(activation, supplied.get(0)));
    }

    /**
     * An event is a frozen ordinary object whose immediate parent is {@code prototype} and whose
     * local slots are exactly {@code level}, {@code message}, {@code fields}, {@code error}, and
     * {@code timestamp}, holding a canonical level String, a String, a frozen structured Map, an
     * attachable Error or {@code null}, and an instant under {@code instantPrototype} or {@code
     * null}.
     */
    static boolean recognizesEvent(
            ProtosActivation activation,
            Object candidate,
            ProtosObjectValue prototype,
            ProtosObjectValue instantPrototype) {
        if (!(candidate instanceof ProtosObjectValue event)
                || candidate instanceof ProtosArrayValue
                || candidate instanceof ProtosMapValue
                || !event.isFrozen()
                || event.parent().orElse(null) != prototype) {
            return false;
        }
        Map<String, Object> slots = event.localSlotsSnapshot();
        if (!slots.keySet().equals(EVENT_SLOTS)) {
            return false;
        }
        if (!(slots.get("level") instanceof ProtosStringValue level)
                || !LEVELS.contains(level.value())
                || !(slots.get("message") instanceof ProtosStringValue)) {
            return false;
        }
        Object error = slots.get("error");
        if (error != ProtosNullValue.INSTANCE && !attachableError(activation, error)) {
            return false;
        }
        Object timestamp = slots.get("timestamp");
        if (timestamp != ProtosNullValue.INSTANCE && !instant(timestamp, instantPrototype)) {
            return false;
        }
        ProtosPrelude prelude = activation.prelude().orElseThrow(() -> invalid(activation));
        return slots.get("fields") instanceof ProtosMapValue fields
                && structured(fields, prelude, new IdentityHashMap<>());
    }

    /**
     * The Error domain is the delegation relation Error handler matching uses; the Error
     * prototype itself is not an attachable Error.
     */
    private static boolean attachableError(ProtosActivation activation, Object value) {
        ProtosPrelude prelude = activation.prelude().orElseThrow(() -> invalid(activation));
        return value != prelude.errorPrototype() && ProtosCoreErrors.isError(activation, value);
    }

    /**
     * The predicate of {@code std:datetime/instant.recognizes}, read from host representation: the
     * immediate parent is the instant module, the local slots are exactly the instant state and
     * behavior slots, and {@code nanoseconds} holds an Integer.
     */
    private static boolean instant(Object value, ProtosObjectValue instantPrototype) {
        if (!(value instanceof ProtosObjectValue object)
                || value instanceof ProtosArrayValue
                || value instanceof ProtosMapValue
                || object.parent().orElse(null) != instantPrototype) {
            return false;
        }
        Map<String, Object> slots = object.localSlotsSnapshot();
        return slots.keySet().equals(INSTANT_SLOTS)
                && ProtosNumericValueSupport.isCurrentInteger(slots.get("nanoseconds"));
    }

    /**
     * Structured data as an event stores it: scalars, and frozen canonical Arrays and Maps with
     * no local slots and String keys, acyclic at every depth. {@code active} holds the containers
     * on the current path so a frozen self-containing container is rejected.
     */
    private static boolean structured(
            Object value, ProtosPrelude prelude, IdentityHashMap<Object, Boolean> active) {
        if (value == ProtosNullValue.INSTANCE
                || value instanceof Boolean
                || value instanceof ProtosStringValue
                // The structured-data scalars stay Integer and Float; D197-C does not extend
                // the logging data contract to Fraction.
                || ProtosNumericValueSupport.isCurrentInteger(value)
                || ProtosNumericValueSupport.isCurrentFloat(value)) {
            return true;
        }
        if (value instanceof ProtosArrayValue array) {
            if (!canonicalContainer(array, prelude.arrayPrototype())
                    || active.put(array, Boolean.TRUE) != null) {
                return false;
            }
            for (Object element : array.indexedSnapshot()) {
                if (!structured(element, prelude, active)) {
                    return false;
                }
            }
            active.remove(array);
            return true;
        }
        if (value instanceof ProtosMapValue map) {
            if (!canonicalContainer(map, prelude.mapPrototype())
                    || active.put(map, Boolean.TRUE) != null) {
                return false;
            }
            for (ProtosMapValue.Entry entry : map.keyedSnapshot()) {
                if (!(entry.key() instanceof ProtosStringValue)
                        || !structured(entry.value(), prelude, active)) {
                    return false;
                }
            }
            active.remove(map);
            return true;
        }
        return false;
    }

    private static boolean canonicalContainer(
            ProtosObjectValue container, ProtosObjectValue prototype) {
        return container.isFrozen()
                && container.parent().orElse(null) == prototype
                && container.localSlotsSnapshot().isEmpty();
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
