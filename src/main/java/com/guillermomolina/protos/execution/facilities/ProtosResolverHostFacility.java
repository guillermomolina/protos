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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosResolverCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.io.ProtosResolverBackend;
import com.guillermomolina.protos.runtime.io.ProtosResolverLookupFlow;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Private host bridge of the source-backed Core {@code Resolver} protocol (D223-A1, NETWORK.md
 * §14).
 *
 * <p>Every portable rule lives in Protos: {@code lib/core/network/Resolver.protos} checks arity
 * and the String argument, admits the name through {@code std:network/internal/DnsNames},
 * rejects numeric address text and normalizes the absolute name before calling {@code lookup}.
 * This frozen object, created by Core bootstrap as the private bootstrap-local anchor {@value
 * #BOOTSTRAP_SLOT} that those methods resolve lexically (never a Prelude binding or module member),
 * supplies only:
 *
 * <ul>
 *   <li>{@code codePoint} and {@code isNfc}: the shared {@link ProtosIdnaUnicodeFactsFacility}
 *       facts;
 *   <li>{@code failed(error)}: an already failed ordinary Future;
 *   <li>{@code lookup(receiver, absoluteName)}: authority-checked submission through {@link
 *       ProtosResolverLookupFlow};
 *   <li>{@code close(receiver)}: the authority-checked lifecycle close.
 * </ul>
 *
 * <p>{@code lookup} and {@code close} answer a failed {@code InvalidIOArgument} Future unless the
 * receiver is a host-granted {@link ProtosResolverCapabilityValue} of the calling Prelude, so the
 * bridge confers no authority of its own. It parses and validates no name: {@code lookup} only
 * checks that it received a String ending with the root dot. The final Actor-domain projection
 * of validated descriptors into canonical frozen IpAddress values and of a failure kind into a
 * fresh occurrence of the source-defined ResolutionError is runtime representation machinery.
 */
public final class ProtosResolverHostFacility {
    public static final String BOOTSTRAP_SLOT = "_coreResolverBridge";

    private ProtosResolverHostFacility() {}

    public static ProtosObjectValue createBridge() {
        ProtosObjectValue bridge = new ProtosObjectValue(ProtosObjectValue.rootObject());
        operation(bridge, "codePoint", 1, ProtosIdnaUnicodeFactsFacility::codePoint);
        operation(bridge, "isNfc", 1, ProtosIdnaUnicodeFactsFacility::isNfc);
        operation(bridge, "failed", 1, ProtosHttpAsyncFacility::failed);
        operation(bridge, "lookup", 2, ProtosResolverHostFacility::lookup);
        operation(bridge, "close", 1, ProtosResolverHostFacility::close);
        return bridge.freeze();
    }

    private static void operation(
            ProtosObjectValue receiver, String name, int arity, ProtosNativeClosureBody body) {
        receiver.createLocalSlot(
                name,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != receiver || supplied.size() != arity) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return body.execute(activation, supplied);
                        }));
    }

    private static Object lookup(ProtosActivation activation, List<?> supplied) {
        ProtosResolverCapabilityValue resolver = grantedOrNull(activation, supplied.get(0));
        if (resolver == null
                || !(supplied.get(1) instanceof ProtosStringValue name)
                || !name.value().endsWith(".")) {
            return invalidArgument(activation);
        }
        return ProtosResolverLookupFlow.lookup(
                resolver, activation, name.value(), PROJECTION);
    }

    private static Object close(ProtosActivation activation, List<?> supplied) {
        ProtosResolverCapabilityValue resolver = grantedOrNull(activation, supplied.get(0));
        if (resolver == null) {
            return invalidArgument(activation);
        }
        return resolver.lifecycleForRuntime(activation).close(activation);
    }

    /** The receiver only when it is a concrete Resolver capability of the calling Prelude. */
    private static ProtosResolverCapabilityValue grantedOrNull(
            ProtosActivation activation, Object receiver) {
        ProtosPrelude prelude = activation.prelude().orElseThrow();
        return receiver instanceof ProtosResolverCapabilityValue resolver
                        && resolver.representedDelegationParent(prelude)
                                == prelude.resolverPrototypeForRuntime()
                ? resolver
                : null;
    }

    private static ProtosFutureValue invalidArgument(ProtosActivation activation) {
        ProtosFutureValue future =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain());
        future.fail(
                ProtosCoreErrors.newOccurrence(
                        activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT));
        return future;
    }

    /** Owning Actor domain only; host callbacks never reach it. */
    static final ProtosResolverLookupFlow.Projection PROJECTION =
            new ProtosResolverLookupFlow.Projection() {
                @Override
                public Object addresses(
                        ProtosActivation activation,
                        List<ProtosResolverBackend.AddressDescriptor> distinct) {
                    ProtosPrelude prelude = activation.prelude().orElseThrow();
                    List<Object> elements = new ArrayList<>(distinct.size());
                    for (ProtosResolverBackend.AddressDescriptor descriptor : distinct) {
                        elements.add(ipAddress(prelude, descriptor));
                    }
                    return prelude.newArray(elements).freeze();
                }

                @Override
                public ProtosObjectValue resolutionError(
                        ProtosActivation activation, ProtosResolverBackend.FailureKind kind) {
                    ProtosObjectValue occurrence =
                            new ProtosObjectValue(
                                    activation.prelude()
                                            .orElseThrow()
                                            .resolutionErrorPrototypeForRuntime());
                    occurrence.createLocalSlot(
                            "kind", new ProtosStringValue(Objects.requireNonNull(kind).kind()));
                    return occurrence;
                }
            };

    private static ProtosObjectValue ipAddress(
            ProtosPrelude prelude, ProtosResolverBackend.AddressDescriptor descriptor) {
        ProtosObjectValue address = new ProtosObjectValue(prelude.ipAddressPrototypeForRuntime());
        address.createLocalSlot("version", ProtosNumericValueSupport.integer(descriptor.version()));
        address.createLocalSlot(
                "bits",
                ProtosNumericValueSupport.integerFromUnsignedBigEndian(
                        descriptor.octets(), prelude));
        address.freeze();
        if (!ProtosStandardIpAddressProtocol.recognizesValue(
                address, prelude.ipAddressPrototypeForRuntime())) {
            throw new IllegalArgumentException("invalid host address projection");
        }
        return address;
    }
}
