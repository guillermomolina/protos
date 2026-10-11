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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * D048 recognition bridge for the ordinary frozen IpAddress prototype/data shape.
 *
 * <p>Construction ({@code call}), structural equality ({@code ==}) and {@code hash} are
 * source closures of {@code lib/core/IpAddress.protos}. Only {@code recognizes} is native: it
 * inspects the candidate's internal shape directly and never dispatches guest behavior.
 */
public final class ProtosStandardIpAddressProtocol {
    private ProtosStandardIpAddressProtocol() {}

    public static ProtosObjectValue install(ProtosObjectValue prototype) {
        Objects.requireNonNull(prototype, "prototype");
        if (prototype.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "standard IpAddress prototype must delegate directly to Object");
        }
        requireSourcePrototype(prototype, "IpAddress", "_coreIpAddressEquals");

        prototype.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> recognizes(activation, supplied, prototype)));
        return prototype.freeze();
    }

    /*
     * The open source prototype owns exactly the source-backed call, hash and
     * equality closures. Equality is written under {@code equalsName} because a
     * source slot target cannot spell an operator; the same Closure is moved to
     * {@code ==}, as Core Integer does for {@code %}.
     */
    static void requireSourcePrototype(
            ProtosObjectValue prototype, String family, String equalsName) {
        if (!prototype.isOpen()
                || !prototype.localSlotsSnapshot().keySet().equals(
                        Set.of("call", equalsName, "hash"))) {
            throw new IllegalStateException(
                    "standard " + family
                            + " prototype must be an open source object with exactly call, "
                            + equalsName + " and hash");
        }
        for (String selector : java.util.List.of("call", equalsName, "hash")) {
            Object value = prototype.readLocalSlot(selector).orElseThrow();
            if (!(value instanceof ProtosClosureValue closure)
                    || closure.definition() == null
                    || closure.executionPlan().isEmpty()
                    || closure.nativeBody().isPresent()) {
                throw new IllegalStateException(
                        "standard " + family + " " + selector + " must be a source-backed Closure");
            }
        }
        Object equality = prototype.readLocalSlot(equalsName).orElseThrow();
        prototype.removeLocalSlot(equalsName);
        prototype.createLocalSlot("==", equality);
    }

    private static Object recognizes(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype) {
        if (activation.receiver() != prototype || supplied.size() != 1) {
            throw invalid(activation);
        }
        return ProtosBooleanValue.of(recognizesValue(supplied.get(0), prototype));
    }

    static boolean recognizesValue(Object candidate, ProtosObjectValue prototype) {
        if (!(candidate instanceof ProtosObjectValue address)
                || !address.isFrozen()
                || address.parent().orElse(null) != prototype) {
            return false;
        }
        java.util.ArrayList<String> slotNames = new java.util.ArrayList<>();
        java.util.ArrayList<Object> slotValues = new java.util.ArrayList<>();
        address.appendLocalBindingsTo(slotNames, slotValues);
        if (slotNames.size() != 2
                || !address.hasLocalSlot("version")
                || !address.hasLocalSlot("bits")) {
            return false;
        }
        return validNumericState(
                address.readLocalSlot("version").orElseThrow(),
                address.readLocalSlot("bits").orElseThrow());
    }

    private static boolean validNumericState(
            Object versionValue, Object bitsValue) {
        if (!ProtosNumericValueSupport.isIntegerInIntRange(versionValue)) {
            return false;
        }

        int ipVersion = ProtosNumericValueSupport.exactInt(versionValue);
        if (ipVersion == 4) {
            return ProtosNumericValueSupport.isUnsignedIntegerWithin(bitsValue, 32);
        }
        if (ipVersion == 6) {
            return ProtosNumericValueSupport.isUnsignedIntegerWithin(bitsValue, 128);
        }
        return false;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
