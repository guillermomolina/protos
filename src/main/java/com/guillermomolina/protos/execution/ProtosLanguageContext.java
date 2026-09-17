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
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
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
    /*
     * PLAT035 leaves one Context-local executable projection cache: Bytecode.
     * Semantic Closure identity remains independent from the executable projection.
     */
    private final ConcurrentMap<ProtosClosureExecutionPlan, ProtosClosureExecutionPlan>
            sharedBytecodeExecutionPlans = new ConcurrentHashMap<>();
    private volatile ProtosTaskCPrimeEntryExecution.Plan taskCPrimeEntryPlan;
    private volatile ProtosTextWriterCPrimeExecution.Plan textWriterCPrimePlan;
    private volatile ProtosTextReaderCPrimeExecution.Plan textReaderCPrimePlan;
    private volatile ProtosBufferedByteReaderCPrimeExecution.Plan bufferedByteReaderCPrimePlan;
    private volatile ProtosBufferedByteWriterCPrimeExecution.Plan bufferedByteWriterCPrimePlan;
    private volatile ProtosIoReleaseCPrimeExecution.Plan ioReleaseCPrimePlan;

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

    ProtosTaskCPrimeEntryExecution.Plan taskCPrimeEntryPlanForRuntime() {
        ProtosTaskCPrimeEntryExecution.Plan existing = taskCPrimeEntryPlan;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (taskCPrimeEntryPlan == null) {
                taskCPrimeEntryPlan =
                        ProtosTaskCPrimeEntryExecution.createPlan(language);
            }
            return taskCPrimeEntryPlan;
        }
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

    ProtosBufferedByteReaderCPrimeExecution.Plan bufferedByteReaderCPrimePlanForRuntime() {
        ProtosBufferedByteReaderCPrimeExecution.Plan existing =
                bufferedByteReaderCPrimePlan;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (bufferedByteReaderCPrimePlan == null) {
                bufferedByteReaderCPrimePlan =
                        ProtosBufferedByteReaderCPrimeExecution.createPlan(language);
            }
            return bufferedByteReaderCPrimePlan;
        }
    }

    ProtosBufferedByteWriterCPrimeExecution.Plan bufferedByteWriterCPrimePlanForRuntime() {
        ProtosBufferedByteWriterCPrimeExecution.Plan existing =
                bufferedByteWriterCPrimePlan;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (bufferedByteWriterCPrimePlan == null) {
                bufferedByteWriterCPrimePlan =
                        ProtosBufferedByteWriterCPrimeExecution.createPlan(language);
            }
            return bufferedByteWriterCPrimePlan;
        }
    }

    ProtosIoReleaseCPrimeExecution.Plan ioReleaseCPrimePlanForRuntime() {
        ProtosIoReleaseCPrimeExecution.Plan existing = ioReleaseCPrimePlan;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (ioReleaseCPrimePlan == null) {
                ioReleaseCPrimePlan =
                        ProtosIoReleaseCPrimeExecution.createPlan(language);
            }
            return ioReleaseCPrimePlan;
        }
    }

    ProtosClosureExecutionPlan bytecodeExecutionPlanForEnteredClosure(
            ProtosClosureValue closure, ProtosClosureExecutionPlan template) {
        Objects.requireNonNull(closure, "closure");
        return bytecodeExecutionPlanForDefinition(
                Objects.requireNonNull(
                        closure.definition(),
                        "entered Closure definition"),
                template);
    }

    /**
     * Returns the Context-owned Bytecode projection for one semantic Closure definition/template.
     *
     * <p>PERF006-C3D uses this definition-based form for P transfer so a transferred Closure does
     * not retain the source Closure object merely to recover its immutable canonical definition.
     * The executable projection remains keyed by the semantic template inside this exact
     * ProtosLanguageContext, preserving PLAT001's one-Context-per-hosted-Process ownership while
     * allowing all P carriers of that Process to share the same executable projection.
     */
    ProtosClosureExecutionPlan bytecodeExecutionPlanForDefinition(
            CanonicalClosure definition, ProtosClosureExecutionPlan template) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(template, "template");
        return sharedBytecodeExecutionPlans.computeIfAbsent(
                template,
                ignored -> template.rebuildBytecodeForLanguage(definition, language));
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
