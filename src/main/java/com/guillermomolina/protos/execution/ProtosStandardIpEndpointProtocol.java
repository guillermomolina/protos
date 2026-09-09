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

/** D048 representation bridge for the ordinary frozen IpEndpoint prototype/data shape. */
public final class ProtosStandardIpEndpointProtocol {
    private static final BigInteger MIN_PORT = BigInteger.ONE;
    private static final BigInteger MAX_PORT = BigInteger.valueOf(65535);
    private static final BigInteger HASH_MULTIPLIER = BigInteger.valueOf(31);
    private static final Set<String> STATE_SLOTS = Set.of("address", "port");

    private ProtosStandardIpEndpointProtocol() {}

    public static ProtosObjectValue install(
            ProtosObjectValue prototype, ProtosObjectValue ipAddressPrototype) {
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(ipAddressPrototype, "ipAddressPrototype");
        if (prototype.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "standard IpEndpoint prototype must delegate directly to Object");
        }
        if (!prototype.isOpen() || !prototype.localSlotsSnapshot().isEmpty()) {
            throw new IllegalStateException(
                    "standard IpEndpoint prototype must be a fresh open source object");
        }
        if (ipAddressPrototype.parent().orElse(null) != ProtosObjectValue.rootObject()
                || !ipAddressPrototype.isFrozen()) {
            throw new IllegalStateException(
                    "standard IpAddress dependency must be the installed frozen Core prototype");
        }

        prototype.createLocalSlot(
                "init",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                initialize(
                                        activation,
                                        supplied,
                                        prototype,
                                        ipAddressPrototype)));
        prototype.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                recognizes(
                                        activation,
                                        supplied,
                                        prototype,
                                        ipAddressPrototype)));
        prototype.createLocalSlot(
                "==",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                equalsValue(
                                        activation,
                                        supplied,
                                        prototype,
                                        ipAddressPrototype)));
        prototype.createLocalSlot(
                "hash",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                hash(
                                        activation,
                                        supplied,
                                        prototype,
                                        ipAddressPrototype)));
        return prototype.freeze();
    }

    private static Object initialize(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype,
            ProtosObjectValue ipAddressPrototype) {
        if (supplied.size() != 2) {
            throw invalid(activation);
        }
        if (!(activation.receiver() instanceof ProtosObjectValue endpoint)
                || endpoint.parent().orElse(null) != prototype
                || !endpoint.isOpen()
                || !endpoint.localSlotsSnapshot().isEmpty()) {
            throw invalid(activation);
        }
        Object addressValue = supplied.get(0);
        Object portValue = supplied.get(1);
        if (!ProtosStandardIpAddressProtocol.recognizesValue(addressValue, ipAddressPrototype)
                || !validPort(portValue)) {
            throw invalid(activation);
        }

        endpoint.createLocalSlot("address", addressValue);
        endpoint.createLocalSlot("port", portValue);
        return endpoint.freeze();
    }

    private static Object recognizes(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype,
            ProtosObjectValue ipAddressPrototype) {
        if (activation.receiver() != prototype || supplied.size() != 1) {
            throw invalid(activation);
        }
        return ProtosBooleanValue.of(
                recognizesValue(supplied.get(0), prototype, ipAddressPrototype));
    }

    private static Object equalsValue(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype,
            ProtosObjectValue ipAddressPrototype) {
        if (supplied.size() != 1
                || !recognizesValue(activation.receiver(), prototype, ipAddressPrototype)) {
            throw invalid(activation);
        }
        Object other = supplied.get(0);
        if (!recognizesValue(other, prototype, ipAddressPrototype)) {
            return ProtosBooleanValue.FALSE;
        }

        ProtosObjectValue left = (ProtosObjectValue) activation.receiver();
        ProtosObjectValue right = (ProtosObjectValue) other;
        ProtosObjectValue leftAddress = addressSlot(left);
        ProtosObjectValue rightAddress = addressSlot(right);
        return ProtosBooleanValue.of(
                ProtosStandardIpAddressProtocol.sameCanonicalState(leftAddress, rightAddress)
                        && portSlot(left).value().equals(portSlot(right).value()));
    }

    private static Object hash(
            ProtosActivation activation,
            List<?> supplied,
            ProtosObjectValue prototype,
            ProtosObjectValue ipAddressPrototype) {
        if (!supplied.isEmpty()
                || !recognizesValue(activation.receiver(), prototype, ipAddressPrototype)) {
            throw invalid(activation);
        }
        ProtosObjectValue endpoint = (ProtosObjectValue) activation.receiver();
        BigInteger addressHash =
                ProtosStandardIpAddressProtocol.canonicalHash(addressSlot(endpoint));
        BigInteger port = portSlot(endpoint).value();
        return new ProtosIntegerValue(addressHash.multiply(HASH_MULTIPLIER).add(port));
    }

    private static boolean recognizesValue(
            Object candidate,
            ProtosObjectValue prototype,
            ProtosObjectValue ipAddressPrototype) {
        if (!(candidate instanceof ProtosObjectValue endpoint)
                || !endpoint.isFrozen()
                || endpoint.parent().orElse(null) != prototype) {
            return false;
        }
        Map<String, Object> slots = endpoint.localSlotsSnapshot();
        if (slots.size() != 2 || !slots.keySet().equals(STATE_SLOTS)) {
            return false;
        }
        return ProtosStandardIpAddressProtocol.recognizesValue(
                        slots.get("address"), ipAddressPrototype)
                && validPort(slots.get("port"));
    }

    private static boolean validPort(Object portValue) {
        if (!(portValue instanceof ProtosIntegerValue port)) {
            return false;
        }
        BigInteger value = port.value();
        return value.compareTo(MIN_PORT) >= 0 && value.compareTo(MAX_PORT) <= 0;
    }

    private static ProtosObjectValue addressSlot(ProtosObjectValue endpoint) {
        return (ProtosObjectValue) endpoint.readLocalSlot("address").orElseThrow();
    }

    private static ProtosIntegerValue portSlot(ProtosObjectValue endpoint) {
        return (ProtosIntegerValue) endpoint.readLocalSlot("port").orElseThrow();
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
