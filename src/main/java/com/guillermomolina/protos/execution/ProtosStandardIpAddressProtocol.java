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
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** D048 representation bridge for the ordinary frozen IpAddress prototype/data shape. */
public final class ProtosStandardIpAddressProtocol {
    private static final Set<String> STATE_SLOTS = Set.of("version", "bits");

    private ProtosStandardIpAddressProtocol() {}

    public static ProtosObjectValue install(ProtosObjectValue prototype) {
        Objects.requireNonNull(prototype, "prototype");
        if (prototype.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "standard IpAddress prototype must delegate directly to Object");
        }
        if (!prototype.isOpen() || !prototype.localSlotsSnapshot().isEmpty()) {
            throw new IllegalStateException(
                    "standard IpAddress prototype must be a fresh open source object");
        }

        prototype.createLocalSlot(
                "init",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> initialize(activation, supplied, prototype)));
        prototype.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> recognizes(activation, supplied, prototype)));
        prototype.createLocalSlot(
                "==",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> equalsValue(activation, supplied, prototype)));
        prototype.createLocalSlot(
                "hash",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> hash(activation, supplied, prototype)));
        return prototype.freeze();
    }

    private static Object initialize(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype) {
        if (supplied.size() != 2) {
            throw invalid(activation);
        }
        if (!(activation.receiver() instanceof ProtosObjectValue address)
                || address.parent().orElse(null) != prototype
                || !address.isOpen()
                || !address.localSlotsSnapshot().isEmpty()) {
            throw invalid(activation);
        }
        Object versionValue = supplied.get(0);
        Object bitsValue = supplied.get(1);
        if (!validNumericState(versionValue, bitsValue)) {
            throw invalid(activation);
        }

        address.createLocalSlot("version", versionValue);
        address.createLocalSlot("bits", bitsValue);
        return address.freeze();
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

    private static Object equalsValue(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype) {
        if (supplied.size() != 1
                || !recognizesValue(activation.receiver(), prototype)) {
            throw invalid(activation);
        }
        Object other = supplied.get(0);
        if (!recognizesValue(other, prototype)) {
            return ProtosBooleanValue.FALSE;
        }

        ProtosObjectValue left = (ProtosObjectValue) activation.receiver();
        ProtosObjectValue right = (ProtosObjectValue) other;
        return ProtosBooleanValue.of(sameCanonicalState(left, right));
    }

    private static Object hash(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype) {
        if (!supplied.isEmpty()
                || !recognizesValue(activation.receiver(), prototype)) {
            throw invalid(activation);
        }
        ProtosObjectValue address = (ProtosObjectValue) activation.receiver();
        return canonicalHash(address, owningPrelude(activation));
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

    static boolean sameCanonicalState(
            ProtosObjectValue left, ProtosObjectValue right) {
        return ProtosNumericValueSupport.sameInteger(
                        integerSlot(left, "version"), integerSlot(right, "version"))
                && ProtosNumericValueSupport.sameInteger(
                        integerSlot(left, "bits"), integerSlot(right, "bits"));
    }

    /**
     * The exact hash {@code bits * 31 + version}. {@code prelude} owns a hash beyond the
     * signed-64 range (IPv6 bits); it is required, because the exact Integer service answers
     * null for an unowned large result and a hash must never be a guest null.
     */
    static Object canonicalHash(ProtosObjectValue address, ProtosPrelude prelude) {
        Objects.requireNonNull(prelude, "prelude");
        Object version = integerSlot(address, "version");
        Object bits = integerSlot(address, "bits");
        return ProtosNumericValueSupport.addIntegers(
                ProtosNumericValueSupport.multiplyIntegers(
                        bits, ProtosNumericValueSupport.integer(31L), prelude),
                version,
                prelude);
    }

    /* The Prelude of the executing domain, which owns every large hash it computes. */
    static ProtosPrelude owningPrelude(ProtosActivation activation) {
        return activation.prelude().orElseThrow(
                () -> new IllegalStateException("IP hashing requires the Core prelude"));
    }

    private static Object integerSlot(ProtosObjectValue address, String name) {
        return ProtosNumericValueSupport.requireCurrentInteger(
                address.readLocalSlot(name).orElseThrow());
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
