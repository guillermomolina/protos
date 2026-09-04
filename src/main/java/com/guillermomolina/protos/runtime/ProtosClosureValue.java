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

package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.execution.ProtosClosureExecutionPlan;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import java.util.List;
import java.util.Objects;

public final class ProtosClosureValue {
    private final CanonicalClosure definition;
    private final List<ProtosObjectValue> capturedLexicalContexts;
    private final Object capturedReceiver;
    private final ProtosObjectValue methodHome;
    private final ProtosReturnHome returnHome;
    private final ProtosPrelude prelude;
    private final ProtosClosureExecutionPlan executionPlan;

    public ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver) {
        this(definition, capturedLexicalContexts, capturedReceiver, null, null, null, null);
    }

    public ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver,
            ProtosReturnHome returnHome) {
        this(
                definition,
                capturedLexicalContexts,
                capturedReceiver,
                null,
                returnHome,
                null,
                null);
    }

    public ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver,
            ProtosObjectValue methodHome,
            ProtosReturnHome returnHome,
            ProtosPrelude prelude) {
        this(
                definition,
                capturedLexicalContexts,
                capturedReceiver,
                methodHome,
                returnHome,
                prelude,
                null);
    }

    public ProtosClosureValue(
            CanonicalClosure definition,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object capturedReceiver,
            ProtosObjectValue methodHome,
            ProtosReturnHome returnHome,
            ProtosPrelude prelude,
            ProtosClosureExecutionPlan executionPlan) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.capturedLexicalContexts =
                List.copyOf(
                        Objects.requireNonNull(
                                capturedLexicalContexts,
                                "capturedLexicalContexts"));
        this.capturedReceiver =
                Objects.requireNonNull(capturedReceiver, "capturedReceiver");
        this.methodHome = methodHome;
        this.returnHome = returnHome;
        this.prelude = prelude;
        this.executionPlan = executionPlan;
    }

    public CanonicalClosure definition() {
        return definition;
    }

    public List<ProtosObjectValue> capturedLexicalContexts() {
        return capturedLexicalContexts;
    }

    public Object capturedReceiver() {
        return capturedReceiver;
    }

    public java.util.Optional<ProtosObjectValue> methodHome() {
        return java.util.Optional.ofNullable(methodHome);
    }

    public java.util.Optional<ProtosReturnHome> returnHome() {
        return java.util.Optional.ofNullable(returnHome);
    }

    public java.util.Optional<ProtosPrelude> prelude() {
        return java.util.Optional.ofNullable(prelude);
    }

    public java.util.Optional<ProtosClosureExecutionPlan> executionPlan() {
        return java.util.Optional.ofNullable(executionPlan);
    }

    public ProtosClosureValue bindMethod(
            Object receiver,
            ProtosObjectValue home) {
        return new ProtosClosureValue(
                definition,
                capturedLexicalContexts,
                Objects.requireNonNull(receiver, "receiver"),
                Objects.requireNonNull(home, "home"),
                returnHome,
                prelude,
                executionPlan);
    }
}
