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
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;
import java.util.Objects;

public final class ProtosStandardIntegerProtocol {
    private ProtosStandardIntegerProtocol() {}

    public static void install(ProtosObjectValue integerPrototype) {
        Objects.requireNonNull(integerPrototype, "integerPrototype");
        requireSourceBackedClosure(integerPrototype, "negated");

        if (integerPrototype.hasLocalSlot("recognizes")) {
            throw new IllegalStateException(
                    "Core Integer already defines a local recognizes slot");
        }
        integerPrototype.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != integerPrototype
                                    || supplied.size() != 1) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return ProtosBooleanValue.of(
                                    supplied.get(0) instanceof ProtosIntegerValue);
                        }));

        installCanonical(integerPrototype, CanonicalIntegerOperation.ADD);
        installCanonical(integerPrototype, CanonicalIntegerOperation.SUBTRACT);
        installCanonical(integerPrototype, CanonicalIntegerOperation.MULTIPLY);
        installCanonical(integerPrototype, CanonicalIntegerOperation.FLOAT_DIVIDE);
        installCanonical(integerPrototype, CanonicalIntegerOperation.QUOTIENT);
        installCanonical(integerPrototype, CanonicalIntegerOperation.REMAINDER);
        installSourceBackedSelector(integerPrototype, "_coreIntegerPercent", "%");
    }

    /**
     * PERF027 guarded-execution capability for the exact standard Integer
     * implementations installed by this protocol. Selector spelling is only
     * one member of the proof: admission additionally requires the exact
     * installed Closure, its private body provenance, the exact Integer
     * prototype home and the current Prelude.
     */
    enum CanonicalIntegerOperation {
        ADD("+", false),
        SUBTRACT("-", false),
        MULTIPLY("*", false),
        FLOAT_DIVIDE("/", true),
        QUOTIENT("div", true),
        REMAINDER("mod", true);

        private final String selector;
        private final boolean requiresNonZeroDivisor;

        CanonicalIntegerOperation(
                String selector,
                boolean requiresNonZeroDivisor) {
            this.selector = selector;
            this.requiresNonZeroDivisor = requiresNonZeroDivisor;
        }

        String selector() {
            return selector;
        }

        boolean requiresNonZeroDivisor() {
            return requiresNonZeroDivisor;
        }
    }

    /**
     * One private implementation object is created for each canonical operation
     * in each bootstrapped Integer prototype. Retaining the exact Closure that
     * owns this body lets classification reject a different Closure that merely
     * wraps the same native body.
     */
    private static final class StandardIntegerBody implements ProtosNativeClosureBody {
        private final ProtosObjectValue home;
        private final CanonicalIntegerOperation operation;
        private ProtosClosureValue owner;

        StandardIntegerBody(
                ProtosObjectValue home,
                CanonicalIntegerOperation operation) {
            this.home = Objects.requireNonNull(home, "home");
            this.operation = Objects.requireNonNull(operation, "operation");
        }

        void bindOwner(ProtosClosureValue owner) {
            if (this.owner != null) {
                throw new IllegalStateException(
                        "standard Integer body already owns a Closure");
            }
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        boolean isCanonicalSelection(
                ProtosClosureValue behavior,
                ProtosObjectValue selectedHome,
                String selector,
                ProtosPrelude prelude) {
            return owner != null
                    && behavior == owner
                    && selectedHome == home
                    && prelude != null
                    && selectedHome == prelude.integerPrototype()
                    && operation.selector().equals(selector);
        }

        @Override
        public Object execute(
                ProtosActivation activation,
                List<?> supplied) {
            return executeCanonicalWithActivation(
                    operation,
                    activation,
                    supplied);
        }
    }

    static CanonicalIntegerOperation canonicalOperationForSelection(
            ProtosClosureValue behavior,
            ProtosObjectValue home,
            String selector,
            ProtosPrelude prelude) {
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(selector, "selector");

        ProtosNativeClosureBody body = behavior.nativeBody().orElse(null);
        if (!(body instanceof StandardIntegerBody standard)
                || !standard.isCanonicalSelection(
                        behavior,
                        home,
                        selector,
                        prelude)) {
            return null;
        }
        return standard.operation;
    }

    /**
     * Executes only a proven canonical operation whose current operands already
     * satisfy the standard native body's successful domain. Null means that the
     * caller must preserve the ordinary native path so Error construction and
     * invocation state remain exactly authoritative there.
     */
    static Object tryExecuteCanonicalOperation(
            CanonicalIntegerOperation operation,
            Object receiver,
            Object[] supplied) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(supplied, "supplied");

        if (supplied.length != 1) {
            return null;
        }
        return tryExecuteCanonicalOperationOne(
                operation, receiver, supplied[0]);
    }

    /**
     * PERF038-H: fixed-arity canonical Integer Send1.
     * No supplied argument vector or caller activation is needed
     * when the canonical operation accepts these exact operands.
     */
    static Object tryExecuteCanonicalOperationOne(
            CanonicalIntegerOperation operation,
            Object receiver,
            Object supplied0) {
        Objects.requireNonNull(operation, "operation");

        if (!(receiver instanceof ProtosIntegerValue integer)) {
            return null;
        }

        if (supplied0 instanceof ProtosFloatValue floating) {
            return switch (operation) {
                case ADD, SUBTRACT, MULTIPLY, FLOAT_DIVIDE ->
                        executeMixedFloatOperation(operation, integer, floating);
                case QUOTIENT, REMAINDER -> null;
            };
        }

        if (!(supplied0 instanceof ProtosIntegerValue argument)
                || (operation.requiresNonZeroDivisor()
                        && argument.signumForRuntime() == 0)) {
            return null;
        }

        return executeValidCanonicalOperation(
                operation, integer, argument);
    }

    private static Object executeCanonicalWithActivation(
            CanonicalIntegerOperation operation,
            ProtosActivation activation,
            List<?> supplied) {
        ProtosIntegerValue receiver = requireIntegerReceiver(activation);
        if (supplied.size() != 1) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
        Object result = tryExecuteCanonicalOperationOne(
                operation, receiver, supplied.get(0));
        if (result == null) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
        return result;
    }

    private static Object executeValidCanonicalOperation(
            CanonicalIntegerOperation operation,
            ProtosIntegerValue receiver,
            ProtosIntegerValue argument) {
        return switch (operation) {
            case ADD -> receiver.addForRuntime(argument);
            case SUBTRACT -> receiver.subtractForRuntime(argument);
            case MULTIPLY -> receiver.multiplyForRuntime(argument);
            case FLOAT_DIVIDE ->
                    new ProtosFloatValue(
                            ProtosBinary64Rounding.divideExactIntegers(
                                    receiver.value(),
                                    argument.value()));
            case QUOTIENT -> receiver.divideForRuntime(argument);
            case REMAINDER -> receiver.remainderForRuntime(argument);
        };
    }

    private static ProtosFloatValue executeMixedFloatOperation(
            CanonicalIntegerOperation operation,
            ProtosIntegerValue receiver,
            ProtosFloatValue argument) {
        double left = ProtosStandardNumericConversionProtocol
                .integerToBinary64(receiver);
        double right = argument.value();
        double result = switch (operation) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case FLOAT_DIVIDE -> left / right;
            case QUOTIENT, REMAINDER ->
                    throw new IllegalArgumentException(
                            "Integer-only operation cannot use a Float operand");
        };
        return new ProtosFloatValue(result);
    }

    private static void installCanonical(
            ProtosObjectValue integerPrototype,
            CanonicalIntegerOperation operation) {
        String selector = operation.selector();
        if (integerPrototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "Core Integer already defines a local " + selector + " slot");
        }

        StandardIntegerBody body =
                new StandardIntegerBody(
                        integerPrototype,
                        operation);
        ProtosClosureValue closure =
                ProtosClosureValue.nativeClosure(body);
        body.bindOwner(closure);
        integerPrototype.createLocalSlot(selector, closure);
    }

    private static void installSourceBackedSelector(
            ProtosObjectValue prototype, String sourceName, String selector) {
        if (prototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "Core Integer already defines a local " + selector + " slot");
        }
        ProtosClosureValue closure = requireSourceBackedClosure(prototype, sourceName);
        prototype.removeLocalSlot(sourceName);
        prototype.createLocalSlot(selector, closure);
    }

    private static ProtosClosureValue requireSourceBackedClosure(
            ProtosObjectValue prototype, String selector) {
        Object value =
                prototype
                        .readLocalSlot(selector)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core Integer source did not define " + selector));
        if (!(value instanceof ProtosClosureValue closure)
                || closure.definition() == null
                || closure.executionPlan().isEmpty()
                || closure.nativeBody().isPresent()) {
            throw new IllegalStateException(
                    "Core Integer." + selector + " must be installed from distributable Core source");
        }
        return closure;
    }

    private static ProtosIntegerValue requireIntegerReceiver(
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosIntegerValue integer)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return integer;
    }
}
