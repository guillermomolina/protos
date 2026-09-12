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
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.util.List;

/** Representation bridge for the immutable standardized Process Environment snapshot. */
public final class ProtosStandardEnvironmentProtocol {
    private static final class StandardPrototype extends ProtosObjectValue {
        StandardPrototype() {
            super(ProtosObjectValue.rootObject());
        }
    }

    private static final ProtosNativeClosureBody STANDARD_EACH_BODY =
            ProtosStandardEnvironmentProtocol::each;
    private static final ProtosClosureValue STANDARD_EACH =
            ProtosClosureValue.nativeClosure(STANDARD_EACH_BODY);

    private ProtosStandardEnvironmentProtocol() {}

    static boolean isStandardEachImplementation(ProtosClosureValue closure) {
        return closure.nativeBody().orElse(null) == STANDARD_EACH_BODY;
    }

    static boolean isCanonicalStandardEachSelection(
            ProtosClosureValue behavior,
            Object receiver,
            ProtosObjectValue home) {
        return behavior == STANDARD_EACH
                && home instanceof StandardPrototype
                && receiver instanceof ProtosEnvironmentValue environment
                && environment.prototypeForRuntime() == home;
    }

    public static ProtosObjectValue createPrototype() {
        ProtosObjectValue prototype = new StandardPrototype();

        prototype.createLocalSlot(
                "get",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosEnvironmentValue environment = requireReceiver(activation);
                            ProtosStringValue name = requireName(supplied, activation);
                            try {
                                var value = environment.getForRuntime(name.value());
                                return value.isPresent()
                                        ? value.get()
                                        : ProtosNullValue.INSTANCE;
                            } catch (IllegalArgumentException invalidRepresentation) {
                                throw error(activation);
                            }
                        }));

        prototype.createLocalSlot(
                "contains",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosEnvironmentValue environment = requireReceiver(activation);
                            ProtosStringValue name = requireName(supplied, activation);
                            final boolean present;
                            try {
                                present = environment.containsForRuntime(name.value());
                            } catch (IllegalArgumentException invalidRepresentation) {
                                throw error(activation);
                            }
                            return present ? ProtosBooleanValue.TRUE : ProtosBooleanValue.FALSE;
                        }));

        prototype.createLocalSlot("each", STANDARD_EACH);

        return prototype.freeze();
    }

    private static Object each(ProtosActivation activation, List<?> supplied) {
        ProtosEnvironmentValue environment = requireReceiver(activation);
        if (supplied.size() != 1) {
            throw error(activation);
        }
        Object block = supplied.get(0);
        requireInvokableForStructured(block, activation);
        List<ProtosEnvironmentValue.PortableEntry> entries =
                portableEntriesForStructured(environment, activation);
        for (ProtosEnvironmentValue.PortableEntry entry : entries) {
            ProtosInvocation.invoke(
                    block,
                    List.of(entry.name(), entry.value()),
                    activation);
        }
        return environment;
    }

    private static ProtosEnvironmentValue requireReceiver(ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosEnvironmentValue environment)) {
            throw error(activation);
        }
        return environment;
    }

    private static ProtosStringValue requireName(
            List<?> supplied, ProtosActivation activation) {
        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosStringValue name)) {
            throw error(activation);
        }
        return name;
    }

    static void requireInvokableForStructured(
            Object candidate,
            ProtosActivation activation) {
        var prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Environment.each requires an owning Core prelude"));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(candidate, "call", prelude)
                            .orElseThrow(() -> error(activation));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw error(activation);
        }
        if (!(selected.value() instanceof ProtosClosureValue)) {
            throw error(activation);
        }
    }

    static List<ProtosEnvironmentValue.PortableEntry> portableEntriesForStructured(
            ProtosEnvironmentValue environment,
            ProtosActivation activation) {
        try {
            /*
             * Preserve the existing whole-snapshot cutover: every native
             * name/value pair is converted, validated and sorted before the
             * first guest callback is prepared.
             */
            return environment.portableEntriesForRuntime();
        } catch (IllegalArgumentException invalidRepresentation) {
            throw error(activation);
        }
    }

    private static ProtosSignalException error(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
