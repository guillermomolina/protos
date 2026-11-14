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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.Objects;

/**
 * I115 (the result of {@code GetMethod}): a selected
 * method together with the receiver it was selected on and the home object
 * that owns the selected slot. Backend-private; never a guest value.
 *
 * <p>It records selection only: no CallTarget, activation or ReturnHome is
 * created here, and it is invoked exclusively through
 * {@link ProtosFunctionCallDispatchers.CallNode}.
 */
final class ProtosBoundMethod {
    private final ProtosClosureValue callable;
    private final Object receiver;
    private final ProtosObjectValue methodHome;

    ProtosBoundMethod(ProtosClosureValue callable, Object receiver, ProtosObjectValue methodHome) {
        this.callable = Objects.requireNonNull(callable, "callable");
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.methodHome = Objects.requireNonNull(methodHome, "methodHome");
    }

    ProtosClosureValue callable() {
        return callable;
    }

    Object receiver() {
        return receiver;
    }

    ProtosObjectValue methodHome() {
        return methodHome;
    }
}
