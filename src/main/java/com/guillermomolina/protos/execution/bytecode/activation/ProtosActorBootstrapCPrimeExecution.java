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
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import java.util.Objects;

/**
 * I158 / D251-A1 backend-private Bytecode DSL root of one complete Task-free Actor initialization.
 *
 * <p>Argument 0 is the bootstrap activation (the semantic caller of both phases) and argument 1 the
 * {@link ProtosActorBootstrap.Sequence} that owns the initialization. The root executes, in one
 * C-prime continuation:
 *
 * <ol>
 *   <li>the prepared initial MODULE/SOURCE initialization ({@code PreparedClosureCall
 *       .moduleInitialization}), whose finish publishes the module record READY and yields the
 *       public MODULE object or SOURCE Context;
 *   <li>the preparation of the bootstrap binding against that finished module; and
 *   <li>the prepared binding invocation, whose ordinary result is returned to the sequence.
 * </ol>
 *
 * <p>Both executable phases use the shared prepared-call dispatcher, so an explicit suspension in
 * the module body, in a nested import or in the binding yields through this root and the retained
 * ContinuationResult resumes this same frame at the same point. Intermediate values live in
 * Bytecode locals; no phase is re-executed after a {@code Yield}. The root creates no Task, no
 * Protos-visible Future and no auxiliary Actor, and owns no second evaluator.
 */
final class ProtosActorBootstrapCPrimeExecution {
    static final class Plan {
        private final RootCallTarget target;

        private Plan(RootCallTarget target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        RootCallTarget target() {
            return target;
        }
    }

    private ProtosActorBootstrapCPrimeExecution() {}

    static Plan planForEnteredContext() {
        ProtosLanguageContext context = ProtosLanguageContext.currentIfEnteredForRuntime();
        if (context == null) {
            throw new IllegalStateException(
                    "Actor bootstrap C-prime entry requires an entered Protos Context");
        }
        return context.actorBootstrapCPrimePlanForRuntime();
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
                                    builder.createLocal("actorBootstrapPreparedCall", null);
                            BytecodeLocal module =
                                    builder.createLocal("actorBootstrapModule", null);
                            BytecodeLocal result =
                                    builder.createLocal("actorBootstrapResult", null);
                            BytecodeLocal childResult =
                                    builder.createLocal("actorBootstrapChildResult", null);
                            BytecodeLocal resumeValue =
                                    builder.createLocal("actorBootstrapResumeValue", null);

                            // Phase 1: the initial MODULE/SOURCE initializer.
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareActorBootstrapModule();
                            builder.emitLoadArgument(1);
                            builder.endPrepareActorBootstrapModule();
                            builder.endStoreLocal();

                            ProtosStructuredDispatchLowerer.emitPreparedInvocationForRuntime(
                                    builder, module, prepared, childResult, resumeValue);

                            // Phase 2: the bootstrap binding on the finished module.
                            builder.beginStoreLocal(prepared);
                            builder.beginPrepareActorBootstrapBinding();
                            builder.emitLoadArgument(1);
                            builder.emitLoadLocal(module);
                            builder.endPrepareActorBootstrapBinding();
                            builder.endStoreLocal();

                            ProtosStructuredDispatchLowerer.emitPreparedInvocationForRuntime(
                                    builder, result, prepared, childResult, resumeValue);

                            builder.beginReturn();
                            builder.emitLoadLocal(result);
                            builder.endReturn();

                            builder.endRoot();
                        });

        return new Plan(roots.getNode(0).getCallTarget());
    }
}
