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
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;
import java.util.Optional;

public final class ProtosClosureExecutionPlan {
    private final ProtosBytecodeClosureExecutionPlan bytecodePlan;

    private ProtosClosureExecutionPlan(
            ProtosBytecodeClosureExecutionPlan bytecodePlan) {
        this.bytecodePlan =
                Objects.requireNonNull(bytecodePlan, "bytecodePlan");
    }

    static ProtosClosureExecutionPlan bytecode(
            CanonicalClosure definition,
            ProtosLanguage language,
            Source source) {
        return new ProtosClosureExecutionPlan(
                new ProtosBytecodeClosureExecutionPlan(
                        definition,
                        language,
                        source));
    }

    static ProtosClosureExecutionPlan bytecode(
            CanonicalClosure definition,
            ProtosLanguage language,
            Source source,
            ProtosBytecodeRootNode bodyRoot) {
        return new ProtosClosureExecutionPlan(
                new ProtosBytecodeClosureExecutionPlan(
                        definition,
                        language,
                        source,
                        bodyRoot));
    }

    boolean isBytecodeBackendForRuntime() {
        return true;
    }

    RootCallTarget bytecodeActivationTargetForComposition() {
        return bytecodePlan.activationTargetForComposition();
    }

    ProtosClosureExecutionPlan rebuildBytecodeForLanguage(
            CanonicalClosure definition,
            ProtosLanguage language) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(language, "language");
        return new ProtosClosureExecutionPlan(
                bytecodePlan.rebuildForLanguage(definition, language));
    }

    Optional<ProtosLanguage> language() {
        return Optional.of(bytecodePlan.language());
    }

    Optional<Source> source() {
        return Optional.of(bytecodePlan.source());
    }

    public void bind(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        bytecodePlan.bind(activation);
    }

    public Object executeBody(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        return bytecodePlan.executeActivation(activation);
    }
}
