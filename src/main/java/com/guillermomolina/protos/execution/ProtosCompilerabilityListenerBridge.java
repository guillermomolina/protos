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

import com.oracle.truffle.api.RootCallTarget;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * TEST009-T reflective bridge from the optimizing Truffle runtime's compilation listener to
 * {@link ProtosCompilerabilityRecorder}.
 *
 * <p>{@code truffle-runtime} is deliberately a runtime-scope dependency, so this class names no
 * {@code com.oracle.truffle.runtime} or {@code com.oracle.truffle.compiler} type at compile time.
 * {@link Contract#resolve} looks up the exact Truffle 25.4 listener overloads that the runtime's
 * listener dispatcher invokes, and a {@link Proxy} implements the listener interface. The
 * compilation target is used through the compile-scope {@link RootCallTarget} API.
 *
 * <p>The bridge is strictly observational: it reads callback arguments only during the callback
 * (GraphInfo is only valid then), never reparses or mutates a root, and never throws back into
 * the runtime, whose dispatcher would rethrow a listener failure into the compilation. A failing
 * callback becomes an {@code error} event instead.
 */
final class ProtosCompilerabilityListenerBridge implements InvocationHandler {
    static final String RUNTIME_CLASS = "com.oracle.truffle.runtime.OptimizedTruffleRuntime";
    static final String LISTENER_INTERFACE =
            "com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener";
    static final String CALL_TARGET_CLASS = "com.oracle.truffle.runtime.OptimizedCallTarget";
    static final String TASK_CLASS = "com.oracle.truffle.runtime.AbstractCompilationTask";
    static final String COMPILATION_TASK_INTERFACE =
            "com.oracle.truffle.compiler.TruffleCompilationTask";
    static final String GRAPH_INFO_INTERFACE =
            "com.oracle.truffle.compiler.TruffleCompilerListener$GraphInfo";
    static final String RESULT_INFO_INTERFACE =
            "com.oracle.truffle.compiler.TruffleCompilerListener$CompilationResultInfo";

    /** The pinned runtime members this bridge depends on, resolved once by exact signature. */
    record Contract(
            Class<?> runtimeClass,
            Class<?> listenerInterface,
            Method addListener,
            Method onStarted,
            Method onTruffleTierFinished,
            Method onGraalTierFinished,
            Method onSuccess,
            Method onFailed,
            Method tier,
            Method countCalls,
            Method countInlinedCalls,
            Method inlinedTargets,
            Method nonTrivialNodeCount,
            Method engineId,
            Field targetId,
            Method graphNodeCount,
            Method graphNodeTypes,
            Method compilationId,
            Method targetCodeSize,
            Method totalFrameSize,
            Method exceptionHandlersCount,
            Method infopointsCount) {

        static Contract resolve(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> runtime = Class.forName(RUNTIME_CLASS, false, loader);
            Class<?> listener = Class.forName(LISTENER_INTERFACE, false, loader);
            Class<?> target = Class.forName(CALL_TARGET_CLASS, false, loader);
            Class<?> task = Class.forName(TASK_CLASS, false, loader);
            Class<?> compilationTask = Class.forName(COMPILATION_TASK_INTERFACE, false, loader);
            Class<?> graph = Class.forName(GRAPH_INFO_INTERFACE, false, loader);
            Class<?> result = Class.forName(RESULT_INFO_INTERFACE, false, loader);
            if (!listener.isInterface() || !RootCallTarget.class.isAssignableFrom(target)) {
                throw new NoSuchMethodException("unexpected listener/call-target type shape");
            }
            Field targetId = target.getField("id");
            if (targetId.getType() != long.class) {
                throw new NoSuchFieldException(CALL_TARGET_CLASS + ".id is not a long");
            }
            return new Contract(
                    runtime,
                    listener,
                    method(runtime, "addListener", void.class, listener),
                    method(listener, "onCompilationStarted", void.class, target, task),
                    method(listener, "onCompilationTruffleTierFinished", void.class, target, task,
                            graph),
                    method(listener, "onCompilationGraalTierFinished", void.class, target, graph),
                    method(listener, "onCompilationSuccess", void.class, target, task, graph,
                            result),
                    method(listener, "onCompilationFailed", void.class, target, String.class,
                            boolean.class, boolean.class, int.class, Supplier.class),
                    method(compilationTask, "tier", int.class),
                    method(task, "countCalls", int.class),
                    method(task, "countInlinedCalls", int.class),
                    method(task, "inlinedTargets", null),
                    method(target, "getNonTrivialNodeCount", int.class),
                    method(target, "engineId", long.class),
                    targetId,
                    method(graph, "getNodeCount", int.class),
                    method(graph, "getNodeTypes", String[].class, boolean.class),
                    method(result, "getCompilationId", long.class),
                    method(result, "getTargetCodeSize", int.class),
                    method(result, "getTotalFrameSize", int.class),
                    method(result, "getExceptionHandlersCount", int.class),
                    method(result, "getInfopointsCount", int.class));
        }

        private static Method method(
                Class<?> owner, String name, Class<?> returnType, Class<?>... parameters)
                throws NoSuchMethodException {
            Method method = owner.getMethod(name, parameters);
            boolean arrayExpected = returnType == null;
            if (arrayExpected ? !method.getReturnType().isArray() : method.getReturnType() != returnType) {
                throw new NoSuchMethodException(owner.getName() + "." + name
                        + " returns " + method.getReturnType().getName());
            }
            return method;
        }
    }

    private final Contract contract;
    private final ProtosCompilerabilityRecorder recorder;

    private ProtosCompilerabilityListenerBridge(
            Contract contract, ProtosCompilerabilityRecorder recorder) {
        this.contract = contract;
        this.recorder = recorder;
    }

    /**
     * Registers the listener on {@code runtime}. Every failure is reported as an {@code
     * install_error} event; the harness treats a missing {@code installed} event as an incomplete
     * acquisition, so an enabled trace never degrades silently into no evidence.
     */
    static void install(Object runtime, ProtosCompilerabilityRecorder recorder) {
        try {
            Contract contract = Contract.resolve(runtime.getClass().getClassLoader());
            if (!contract.runtimeClass().isInstance(runtime)) {
                recorder.installError("RUNTIME_NOT_OPTIMIZING", runtime.getClass().getName());
                return;
            }
            Object listener = Proxy.newProxyInstance(
                    contract.listenerInterface().getClassLoader(),
                    new Class<?>[] {contract.listenerInterface()},
                    new ProtosCompilerabilityListenerBridge(contract, recorder));
            contract.addListener().invoke(runtime, listener);
            recorder.installed(runtime.getClass().getName());
        } catch (ClassNotFoundException | NoClassDefFoundError missing) {
            recorder.installError("RUNTIME_CLASS_MISSING", describe(missing));
        } catch (NoSuchMethodException | NoSuchFieldException shape) {
            recorder.installError("CALLBACK_SHAPE_INCOMPATIBLE", describe(shape));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            recorder.installError("LISTENER_INSTALL_FAILED", describe(failure));
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                // In-process only: the runtime keeps listeners in a list that may hash them.
                case "hashCode" -> System.identityHashCode(proxy);
                default -> "ProtosCompilerabilityListener";
            };
        }
        try {
            if (method.equals(contract.onStarted())) {
                onStarted(args[0], args[1]);
            } else if (method.equals(contract.onTruffleTierFinished())) {
                onTruffleTierFinished(args[0], args[1], args[2]);
            } else if (method.equals(contract.onGraalTierFinished())) {
                recorder.graalTierFinished(args[0], nodeCount(args[1]), nodeTypes(args[1]));
            } else if (method.equals(contract.onSuccess())) {
                onSuccess(args[0], args[3]);
            } else if (method.equals(contract.onFailed())) {
                recorder.failed(args[0], (String) args[1], (Boolean) args[2], (Boolean) args[3],
                        (Integer) args[4]);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            recorder.error("CALLBACK_FAILED", method.getName() + ": " + describe(failure));
        }
        // Every OptimizedTruffleRuntimeListener instance method is void.
        return null;
    }

    private void onStarted(Object target, Object task) throws ReflectiveOperationException {
        ProtosCompilerabilityRootIdentity identity =
                ProtosCompilerabilityRootIdentity.describe(((RootCallTarget) target).getRootNode());
        recorder.started(
                target,
                identity,
                (Integer) contract.tier().invoke(task),
                (Integer) contract.nonTrivialNodeCount().invoke(target),
                (Long) contract.engineId().invoke(target),
                contract.targetId().getLong(target));
    }

    private void onTruffleTierFinished(Object target, Object task, Object graph)
            throws ReflectiveOperationException {
        List<String> inlinedKeys = new ArrayList<>();
        for (Object inlined : (Object[]) contract.inlinedTargets().invoke(task)) {
            inlinedKeys.add(inlined instanceof RootCallTarget callTarget
                    ? ProtosCompilerabilityRootIdentity.describe(callTarget.getRootNode()).durableKey()
                    : null);
        }
        recorder.truffleTierFinished(
                target,
                nodeCount(graph),
                nodeTypes(graph),
                new ProtosCompilerabilityRecorder.Inlining(
                        (Integer) contract.countCalls().invoke(task),
                        (Integer) contract.countInlinedCalls().invoke(task),
                        inlinedKeys));
    }

    private void onSuccess(Object target, Object result) throws ReflectiveOperationException {
        recorder.succeeded(target, new ProtosCompilerabilityRecorder.Result(
                (Long) contract.compilationId().invoke(result),
                (Integer) contract.targetCodeSize().invoke(result),
                (Integer) contract.totalFrameSize().invoke(result),
                (Integer) contract.exceptionHandlersCount().invoke(result),
                (Integer) contract.infopointsCount().invoke(result)));
    }

    private int nodeCount(Object graph) throws ReflectiveOperationException {
        return (Integer) contract.graphNodeCount().invoke(graph);
    }

    private String[] nodeTypes(Object graph) throws ReflectiveOperationException {
        return (String[]) contract.graphNodeTypes().invoke(graph, true);
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : failure;
        return cause.getClass().getName() + ": " + cause.getMessage();
    }
}
