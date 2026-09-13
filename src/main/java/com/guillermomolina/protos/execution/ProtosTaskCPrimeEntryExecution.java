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
 * <p>The root owns no semantic dispatch policy. Argument 0 remains the semantic
 * invocation activation and argument 1 is the same PreparedClosureCall carrier
 * used by ordinary canonical Bytecode lowering. The root executes the exact
 * shared prepared-call dispatcher, including structured control, native-leaf
 * suspension, nested C-prime yield/resume and ReturnHome completion.
 *
 * <p>B6B-E2 routes Task-backed native Closure/method entries through this
 * Context-local plan. Source-backed Task Closures keep their already-established
 * direct prepared-root path; both routes share the same C-prime continuation
 * publication machinery.
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

    static void execute(
            ProtosTask task,
            ProtosBytecodeRootNode.PreparedClosureCall prepared) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(prepared, "prepared");
        ProtosBytecodeTaskExecution.executePreparedEntry(
                task,
                planForEnteredContext().target(),
                prepared);
    }

    static Plan createPlan(ProtosLanguage language) {
        Objects.requireNonNull(language, "language");

        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal prepared =
                                    builder.createLocal("taskEntryPreparedCall", null);
                            BytecodeLocal result =
                                    builder.createLocal("taskEntryResult", null);
                            BytecodeLocal childResult =
                                    builder.createLocal("taskEntryChildResult", null);
                            BytecodeLocal resumeValue =
                                    builder.createLocal("taskEntryResumeValue", null);

                            builder.beginStoreLocal(prepared);
                            builder.emitLoadArgument(1);
                            builder.endStoreLocal();

                            CanonicalToBytecodeLowerer.emitPreparedInvocationForRuntime(
                                    builder,
                                    result,
                                    prepared,
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
