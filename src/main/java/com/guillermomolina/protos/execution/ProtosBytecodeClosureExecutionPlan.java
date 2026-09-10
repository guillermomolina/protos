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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;

/**
 * Internal Bytecode DSL execution plan for the first Closure migration seam.
 *
 * <p>PERF006-B2C3A supports general required positional parameters plus a
 * trailing rest parameter for Closure bodies whose canonical expressions are
 * already supported by {@link CanonicalToBytecodeLowerer}. Default binding and
 * normal Closure dispatch remain staged for later PERF006-B slices.</p>
 */
final class ProtosBytecodeClosureExecutionPlan {
    private final CanonicalClosure definition;
    private final ProtosLanguage language;
    private final Source source;
    private final ProtosBytecodeRootNode bodyRoot;
    private final RootCallTarget bodyTarget;

    ProtosBytecodeClosureExecutionPlan(
            CanonicalClosure definition,
            ProtosLanguage language,
            Source source) {
        this(
                definition,
                language,
                source,
                new CanonicalToBytecodeLowerer(
                                Objects.requireNonNull(language, "language"),
                                Objects.requireNonNull(source, "source"))
                        .lowerRoot(
                                Objects.requireNonNull(definition, "definition")
                                        .body()));
    }

    ProtosBytecodeClosureExecutionPlan(
            CanonicalClosure definition,
            ProtosLanguage language,
            Source source,
            ProtosBytecodeRootNode bodyRoot) {
        this.definition =
                Objects.requireNonNull(definition, "definition");
        this.language =
                Objects.requireNonNull(language, "language");
        this.source =
                Objects.requireNonNull(source, "source");

        for (int index = 0; index < definition.parameters().size(); index++) {
            CanonicalParameter parameter = definition.parameters().get(index);
            if (parameter.rest() && index != definition.parameters().size() - 1) {
                throw new IllegalArgumentException("rest parameter must be trailing");
            }
            if (parameter.defaultValue().isPresent()) {
                throw new UnsupportedOperationException(
                        "PERF006-B2C3A default parameter binding is deferred");
            }
        }

        this.bodyRoot =
                Objects.requireNonNull(bodyRoot, "bodyRoot");
        this.bodyTarget = bodyRoot.getCallTarget();
    }

    CanonicalClosure definition() {
        return definition;
    }

    ProtosLanguage language() {
        return language;
    }

    Source source() {
        return source;
    }

    ProtosBytecodeClosureExecutionPlan rebuildForLanguage(
            CanonicalClosure newDefinition,
            ProtosLanguage newLanguage) {
        return new ProtosBytecodeClosureExecutionPlan(
                Objects.requireNonNull(newDefinition, "newDefinition"),
                Objects.requireNonNull(newLanguage, "newLanguage"),
                source);
    }

    ProtosBytecodeRootNode bodyRootForTesting() {
        return bodyRoot;
    }

    RootCallTarget bodyTargetForComposition() {
        return bodyTarget;
    }

    void bind(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        java.util.List<Object> supplied =
                activation.arguments()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "parameter binding requires an invocation activation"))
                        .indexedSnapshot();
        ProtosPrelude prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "parameter binding requires an owning Core prelude"));

        int suppliedIndex = 0;
        for (CanonicalParameter parameter : definition.parameters()) {
            if (parameter.rest()) {
                ProtosArrayValue rest =
                        prelude.newFrozenArray(
                                supplied.subList(
                                        suppliedIndex,
                                        supplied.size()));
                createParameterSlot(
                        activation,
                        parameter.name(),
                        rest);
                suppliedIndex = supplied.size();
                continue;
            }

            if (suppliedIndex >= supplied.size()) {
                throw argumentCountError(activation);
            }

            createParameterSlot(
                    activation,
                    parameter.name(),
                    supplied.get(suppliedIndex));
            suppliedIndex++;
        }

        if (suppliedIndex < supplied.size()) {
            throw argumentCountError(activation);
        }
    }

    private static void createParameterSlot(
            ProtosActivation activation,
            String name,
            Object value) {
        try {
            activation.context().createLocalSlot(name, value);
        } catch (IllegalStateException invalidCreation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }
    }

    private static ProtosSignalException argumentCountError(
            ProtosActivation activation) {
        return new ProtosSignalException(
                ProtosCoreErrors.newError(activation));
    }

    /**
     * Execute with the same activation calling convention as every existing
     * Protos AST root: frame argument 0 is the exact invocation activation.
     */
    Object executeBody(ProtosActivation activation) {
        bind(activation);
        return bodyTarget.call(activation);
    }
}
