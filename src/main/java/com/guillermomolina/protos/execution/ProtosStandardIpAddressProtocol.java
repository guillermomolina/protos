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
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** D048 representation bridge for the ordinary frozen IpAddress prototype/data shape. */
public final class ProtosStandardIpAddressProtocol {
    private static final BigInteger IPV4_MAX =
            BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
    private static final BigInteger IPV6_MAX =
            BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final BigInteger HASH_MULTIPLIER = BigInteger.valueOf(31);
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
        return ProtosBooleanValue.of(
                integerSlot(left, "version").value().equals(integerSlot(right, "version").value())
                        && integerSlot(left, "bits").value().equals(integerSlot(right, "bits").value()));
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
        BigInteger version = integerSlot(address, "version").value();
        BigInteger bits = integerSlot(address, "bits").value();
        return new ProtosIntegerValue(bits.multiply(HASH_MULTIPLIER).add(version));
    }

    private static boolean recognizesValue(Object candidate, ProtosObjectValue prototype) {
        if (!(candidate instanceof ProtosObjectValue address)
                || !address.isFrozen()
                || address.parent().orElse(null) != prototype) {
            return false;
        }
        Map<String, Object> slots = address.localSlotsSnapshot();
        if (slots.size() != 2 || !slots.keySet().equals(STATE_SLOTS)) {
            return false;
        }
        return validNumericState(slots.get("version"), slots.get("bits"));
    }

    private static boolean validNumericState(Object versionValue, Object bitsValue) {
        if (!(versionValue instanceof ProtosIntegerValue version)
                || !(bitsValue instanceof ProtosIntegerValue bits)) {
            return false;
        }
        BigInteger versionNumber = version.value();
        BigInteger bitsNumber = bits.value();
        if (bitsNumber.signum() < 0) {
            return false;
        }
        if (versionNumber.equals(BigInteger.valueOf(4))) {
            return bitsNumber.compareTo(IPV4_MAX) <= 0;
        }
        if (versionNumber.equals(BigInteger.valueOf(6))) {
            return bitsNumber.compareTo(IPV6_MAX) <= 0;
        }
        return false;
    }

    private static ProtosIntegerValue integerSlot(ProtosObjectValue address, String name) {
        return (ProtosIntegerValue) address.readLocalSlot(name).orElseThrow();
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
