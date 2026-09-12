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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Host-side state owned by one initialized Polyglot Protos context. */
final class ProtosLanguageContext {
    private static final TruffleLanguage.ContextReference<ProtosLanguageContext> REFERENCE =
            TruffleLanguage.ContextReference.create(ProtosLanguage.class);

    private final ProtosLanguage language;
    private final TruffleLanguage.Env env;
    private final ConcurrentMap<ProtosClosureExecutionPlan, ProtosClosureExecutionPlan>
            sharedExecutionPlans = new ConcurrentHashMap<>();

    /*
     * PERF006-B6A6A1 keeps the historical AST projection cache as the B6B oracle and
     * owns a separate C-prime projection cache. Both are Context-local and disappear
     * with this ProtosLanguageContext; neither changes semantic Closure identity.
     */
    private final ConcurrentMap<ProtosClosureExecutionPlan, ProtosClosureExecutionPlan>
            sharedBytecodeExecutionPlans = new ConcurrentHashMap<>();
    private volatile ProtosTextWriterCPrimeExecution.Plan textWriterCPrimePlan;
    private volatile ProtosTextReaderCPrimeExecution.Plan textReaderCPrimePlan;

    ProtosLanguageContext(ProtosLanguage language, TruffleLanguage.Env env) {
        this.language = Objects.requireNonNull(language, "language");
        this.env = Objects.requireNonNull(env, "env");
    }

    static ProtosLanguageContext current() {
        return REFERENCE.get(null);
    }

    static ProtosLanguageContext currentIfEnteredForRuntime() {
        try {
            org.graalvm.polyglot.Context.getCurrent();
        } catch (IllegalStateException noEnteredContext) {
            return null;
        }
        return REFERENCE.get(null);
    }

    ProtosTextWriterCPrimeExecution.Plan textWriterCPrimePlanForRuntime() {
        ProtosTextWriterCPrimeExecution.Plan existing = textWriterCPrimePlan;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (textWriterCPrimePlan == null) {
                textWriterCPrimePlan =
                        ProtosTextWriterCPrimeExecution.createPlan(language);
            }
            return textWriterCPrimePlan;
        }
    }

    ProtosTextReaderCPrimeExecution.Plan textReaderCPrimePlanForRuntime() {
        ProtosTextReaderCPrimeExecution.Plan existing = textReaderCPrimePlan;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (textReaderCPrimePlan == null) {
                textReaderCPrimePlan =
                        ProtosTextReaderCPrimeExecution.createPlan(language);
            }
            return textReaderCPrimePlan;
        }
    }

    ProtosClosureExecutionPlan executionPlanForEnteredClosure(
            ProtosClosureValue closure, ProtosClosureExecutionPlan template) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(template, "template");
        return sharedExecutionPlans.computeIfAbsent(
                template,
                ignored ->
                        template.rebuildAstForLanguage(
                                Objects.requireNonNull(
                                        closure.definition(),
                                        "entered Closure definition"),
                                language));
    }

    ProtosClosureExecutionPlan bytecodeExecutionPlanForEnteredClosure(
            ProtosClosureValue closure, ProtosClosureExecutionPlan template) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(template, "template");
        return sharedBytecodeExecutionPlans.computeIfAbsent(
                template,
                ignored ->
                        template.rebuildBytecodeForLanguage(
                                Objects.requireNonNull(
                                        closure.definition(),
                                        "entered Closure definition"),
                                language));
    }

    ProtosClosureExecutionPlan executionPlanForSharedClosure(ProtosClosureValue closure) {
        Objects.requireNonNull(closure, "closure");
        if (!closure.requiresContextLocalExecutionProjectionForRuntime()) {
            throw new IllegalArgumentException(
                    "Closure does not require Context-local execution projection");
        }
        ProtosClosureExecutionPlan template =
                closure.executionPlan()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "shared Closure has no execution-plan template"));
        return executionPlanForEnteredClosure(closure, template);
    }

    ProtosClosureExecutionPlan projectedExecutionPlanForTesting(ProtosClosureValue closure) {
        Objects.requireNonNull(closure, "closure");
        return closure.executionPlan().map(sharedExecutionPlans::get).orElse(null);
    }

    int projectedExecutionPlanCountForTesting() {
        return sharedExecutionPlans.size();
    }

    int projectedBytecodeExecutionPlanCountForTesting() {
        return sharedBytecodeExecutionPlans.size();
    }

    ProtosLanguage languageForRuntime() {
        return language;
    }

    ProtosLanguage languageForTesting() {
        return languageForRuntime();
    }

    Source materializeModuleSource(ProtosModuleSource source) {
        Objects.requireNonNull(source, "source");
        return source.physicalPath()
                .map(path -> materializeFileSource(path, source.characters()))
                .orElseGet(source::literalSource);
    }

    Source materializeFileSource(Path path, CharSequence characters) {
        Path exact = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Objects.requireNonNull(characters, "characters");
        ProtosPolyglotExecutionContext.admitPhysicalSourceForRuntime(exact);
        TruffleFile file = env.getPublicTruffleFile(exact.toString());
        return Source.newBuilder(ProtosLanguage.ID, file)
                .canonicalizePath(false)
                .content(characters)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    CallTarget parsePublic(Source source) {
        Objects.requireNonNull(source, "source");
        if (!ProtosLanguage.ID.equals(source.getLanguage())) {
            throw new IllegalArgumentException("Source belongs to another language");
        }
        return env.parsePublic(source);
    }

    TruffleLanguage.Env env() {
        return env;
    }
}
