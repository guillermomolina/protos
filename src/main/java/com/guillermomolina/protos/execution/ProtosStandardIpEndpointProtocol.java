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

/**
 * D048 recognition bridge for the ordinary frozen IpEndpoint prototype/data shape.
 *
 * <p>Construction ({@code call}), structural equality ({@code ==}) and {@code hash} are
 * source closures of {@code lib/core/IpEndpoint.protos}. Only {@code recognizes} is native: it
 * inspects the candidate's internal shape, including its address, directly and never
 * dispatches guest behavior.
 */
public final class ProtosStandardIpEndpointProtocol {
    private ProtosStandardIpEndpointProtocol() {}

    public static ProtosObjectValue install(
            ProtosObjectValue prototype, ProtosObjectValue ipAddressPrototype) {
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(ipAddressPrototype, "ipAddressPrototype");
        if (prototype.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "standard IpEndpoint prototype must delegate directly to Object");
        }
        ProtosStandardIpAddressProtocol.requireSourcePrototype(
                prototype, "IpEndpoint", "_coreIpEndpointEquals");
        if (ipAddressPrototype.parent().orElse(null) != ProtosObjectValue.rootObject()
                || !ipAddressPrototype.isFrozen()) {
            throw new IllegalStateException(
                    "standard IpAddress dependency must be the installed frozen Core prototype");
        }

        prototype.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) ->
                                recognizes(
                                        activation,
                                        supplied,
                                        prototype,
                                        ipAddressPrototype)));
        return prototype.freeze();
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

    static boolean recognizesValue(
            Object candidate,
            ProtosObjectValue prototype,
            ProtosObjectValue ipAddressPrototype) {
        if (!(candidate instanceof ProtosObjectValue endpoint)
                || !endpoint.isFrozen()
                || endpoint.parent().orElse(null) != prototype) {
            return false;
        }
        java.util.ArrayList<String> slotNames = new java.util.ArrayList<>();
        java.util.ArrayList<Object> slotValues = new java.util.ArrayList<>();
        endpoint.appendLocalBindingsTo(slotNames, slotValues);
        if (slotNames.size() != 2
                || !endpoint.hasLocalSlot("address")
                || !endpoint.hasLocalSlot("port")) {
            return false;
        }
        return ProtosStandardIpAddressProtocol.recognizesValue(
                        endpoint.readLocalSlot("address").orElseThrow(),
                        ipAddressPrototype)
                && validPort(
                        endpoint.readLocalSlot("port").orElseThrow());
    }

    private static boolean validPort(Object portValue) {
        if (!ProtosNumericValueSupport.isIntegerInIntRange(portValue)) {
            return false;
        }
        int value = ProtosNumericValueSupport.exactInt(portValue);
        return value >= 1 && value <= 65535;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
