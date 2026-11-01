/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import java.util.Objects;

/**
 * Backend-private B6B entry root for one already-prepared Task-owned call.
 *
 * <p>I115: the root owns no semantic dispatch policy. Argument 0 is the
 * activation of a structured standard native and argument 1 its
 * {@link ProtosPreparedCalls.StructuredNativeInvocation} state; it is entered
 * only by {@link ProtosBuiltinCallRootNode}. Every callback the structured
 * protocol makes goes through the shared dispatcher; its suspensions compose
 * through this root's own Bytecode DSL continuation.
 */
final class ProtosTaskCPrimeEntryExecution {
    static final class Plan {
        private final RootCallTarget target;

        private Plan(RootCallTarget target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        RootCallTarget target() {
            return target;
        }
    }

    private ProtosTaskCPrimeEntryExecution() {}

    static Plan planForEnteredContext() {
        ProtosLanguageContext context =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (context == null) {
            throw new IllegalStateException(
                    "Task C-prime entry requires an entered Protos Context");
        }
        return context.taskCPrimeEntryPlanForRuntime();
    }

    /**
     * The Context-local structured-protocol root, entered with {@code
     * (activation, structured)}; it keeps ownership of the protocol's
     * regions, cleanup and finish.
     */
    static RootCallTarget structuredDispatchTargetForEnteredContext() {
        return planForEnteredContext().target();
    }

    static Plan createPlan(ProtosLanguage language) {
        Objects.requireNonNull(language, "language");

        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal structured =
                                    builder.createLocal("structuredNativeInvocation", null);
                            BytecodeLocal result =
                                    builder.createLocal("taskEntryResult", null);
                            BytecodeLocal childResult =
                                    builder.createLocal("taskEntryChildResult", null);
                            BytecodeLocal resumeValue =
                                    builder.createLocal("taskEntryResumeValue", null);

                            builder.beginStoreLocal(structured);
                            builder.emitLoadArgument(1);
                            builder.endStoreLocal();

                            ProtosStructuredDispatchLowerer.emitStructuredInvocationForRuntime(
                                    builder,
                                    result,
                                    structured,
                                    childResult,
                                    resumeValue);

                            builder.beginReturn();
                            builder.emitLoadLocal(result);
                            builder.endReturn();

                            builder.endRoot();
                        });

        return new Plan(roots.getNode(0).getCallTarget());
    }
}
