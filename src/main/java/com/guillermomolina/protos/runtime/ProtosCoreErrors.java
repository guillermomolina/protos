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
package com.guillermomolina.protos.runtime;

import java.util.Objects;

/** Typed construction/signaling API for the closed standard Core Error taxonomy. */
public final class ProtosCoreErrors {
    public enum StandardError {
        ERROR("Error"),
        INVALID_RETURN("InvalidReturn"),
        SLOT_NOT_FOUND("SlotNotFound"),
        CANCELLED("Cancelled"),
        FUTURE_RESOLUTION_CYCLE("FutureResolutionCycle"),
        REQUEST_OUTCOME_UNCERTAIN("RequestOutcomeUncertain"),
        NON_TRANSFERABLE_VALUE("NonTransferableValue"),
        NON_PARALLEL_VALUE("NonParallelValue"),
        INVALID_PREDICATE_RESULT("InvalidPredicateResult"),
        INVALID_COMPARATOR_RESULT("InvalidComparatorResult"),
        INVALID_COMPARATOR_ORDER("InvalidComparatorOrder"),
        PARALLEL_REGION_OVERLAP("ParallelRegionOverlap"),
        PARALLEL_REGION_IN_USE("ParallelRegionInUse"),
        PARALLEL_REGION_OUTSIDE_P("ParallelRegionOutsideP"),
        I_O_ERROR("IOError"),
        INVALID_I_O_ARGUMENT("InvalidIOArgument"),
        I_O_LIFECYCLE_ERROR("IOLifecycleError"),
        I_O_CAPACITY_EXHAUSTED("IOCapacityExhausted"),
        ENCODING_ERROR("EncodingError"),
        LINE_TOO_LONG("LineTooLong");
        private final String prototypeName;
        StandardError(String prototypeName) { this.prototypeName = prototypeName; }
        public String prototypeName() { return prototypeName; }
    }

    private ProtosCoreErrors() {}

    private static ProtosPrelude requirePrelude(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        return activation.prelude().orElseThrow(
                () -> new IllegalStateException(
                        "standard Error taxonomy is unavailable before Core prelude bootstrap"));
    }

    public static ProtosObjectValue prototype(
            ProtosActivation activation, StandardError standardError) {
        Objects.requireNonNull(standardError, "standardError");
        ProtosPrelude prelude = requirePrelude(activation);
        return standardError == StandardError.ERROR
                ? prelude.errorPrototype()
                : prelude.standardErrorPrototype(standardError.prototypeName());
    }

    public static ProtosObjectValue newOccurrence(
            ProtosActivation activation, StandardError standardError) {
        return new ProtosObjectValue(prototype(activation, standardError));
    }

    public static ProtosObjectValue newError(ProtosActivation activation) {
        return newOccurrence(activation, StandardError.ERROR);
    }

    public static ProtosObjectValue newInvalidReturn(ProtosActivation activation) {
        return newOccurrence(activation, StandardError.INVALID_RETURN);
    }

    public static ProtosObjectValue newSlotNotFound(ProtosActivation activation) {
        return newOccurrence(activation, StandardError.SLOT_NOT_FOUND);
    }

    public static ProtosObjectValue newUnqualifiedLookupError(ProtosActivation activation) {
        return newSlotNotFound(activation);
    }

    public static boolean isError(ProtosActivation activation, Object value) {
        if (!(value instanceof ProtosObjectValue object)) return false;
        ProtosObjectValue root = requirePrelude(activation).errorPrototype();
        ProtosObjectValue current = object;
        while (true) {
            if (current == root) return true;
            Object parent = current.parent().orElse(null);
            if (!(parent instanceof ProtosObjectValue parentObject)) return false;
            current = parentObject;
        }
    }

    /**
     * Build the host control transfer for an already-existing Error object.
     * This never clones, enriches, wraps, or coerces the supplied value.
     */
    public static ProtosSignalException signal(
            ProtosActivation activation, ProtosObjectValue error) {
        Objects.requireNonNull(error, "error");
        if (!isError(activation, error)) {
            throw new IllegalArgumentException(
                    "semantic signaling requires an Error object from the current Core prelude");
        }
        return new ProtosSignalException(error);
    }
}
