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

import com.guillermomolina.protos.runtime.ProtosTask;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Backend-private resumable leaf produced by a PLAT019 suspension-capable native implementation.
 *
 * <p>The Java activation that produced this object is not retained as a continuation. All state
 * required to finish the logical native operation is explicit in this descriptor/resumer. The
 * surrounding Bytecode root yields this object so Truffle materializes the real guest C-prime
 * continuation.
 */
final class ProtosNativeSuspension {
    private final ProtosTask.WaitDependency dependency;
    private final Supplier<Object> resumer;
    private boolean resumed;

    private ProtosNativeSuspension(
            ProtosTask.WaitDependency dependency,
            Supplier<Object> resumer) {
        this.dependency =
                Objects.requireNonNull(
                        dependency,
                        "dependency");
        this.resumer =
                Objects.requireNonNull(
                        resumer,
                        "resumer");
    }

    static ProtosNativeSuspension pending(
            ProtosTask.WaitDependency dependency,
            Supplier<Object> resumer) {
        return new ProtosNativeSuspension(
                dependency,
                resumer);
    }

    ProtosTask.WaitDependency dependency() {
        return dependency;
    }

    Object resume() {
        Supplier<Object> resumeAction;
        synchronized (this) {
            if (resumed) {
                throw new IllegalStateException(
                        "native suspension descriptor is one-shot");
            }
            resumed = true;
            resumeAction = resumer;
        }
        return Objects.requireNonNull(
                resumeAction.get(),
                "native suspension resumer returned null");
    }
}
