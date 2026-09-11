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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;

final class ProtosStandardImportProtocol {
    private ProtosStandardImportProtocol() {}

    /*
     * PLAT025 provenance lives in the installed bridge body itself rather than
     * in a global registry or selector spelling. The body is still an ordinary
     * native Closure implementation when it is reached outside the exact
     * post-lookup Bytecode intrinsic.
     */
    private static final class StandardImportBody implements ProtosNativeClosureBody {
        private final ProtosModuleRuntime runtime;

        StandardImportBody(ProtosModuleRuntime runtime) {
            this.runtime = Objects.requireNonNull(runtime, "runtime");
        }

        ProtosModuleRuntime runtime() {
            return runtime;
        }

        @Override
        public Object execute(ProtosActivation activation, java.util.List<?> supplied) {
            if (supplied.size() != 1) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
            return runtime.importModule(supplied.get(0), activation);
        }
    }

    static ProtosModuleRuntime selectedRuntimeForBytecodeIntrinsic(
            Object receiver,
            ProtosClosureValue selectedBehavior,
            ProtosObjectValue selectedHome,
            ProtosPrelude prelude) {
        if (prelude == null) {
            return null;
        }
        Object standardBinding =
                prelude.bindings().readLocalSlot("import").orElse(null);
        if (!(standardBinding instanceof ProtosObjectValue standardFacility)
                || receiver != standardFacility
                || selectedHome != standardFacility
                || standardFacility.readLocalSlot("call").orElse(null) != selectedBehavior) {
            return null;
        }
        ProtosNativeClosureBody body = selectedBehavior.nativeBody().orElse(null);
        if (!(body instanceof StandardImportBody standardBody)) {
            return null;
        }
        return standardBody.runtime();
    }

    static ProtosObjectValue installImportFacility(
            ProtosObjectValue facility, ProtosModuleRuntime runtime) {
        Objects.requireNonNull(facility, "facility");
        Objects.requireNonNull(runtime, "runtime");
        if (facility.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "Core import facility must delegate directly to Object");
        }
        if (!facility.isOpen() || !facility.localSlotsSnapshot().isEmpty()) {
            throw new IllegalArgumentException(
                    "source-created Core import facility must begin open and without local slots");
        }

        facility.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(new StandardImportBody(runtime)));
        return facility.freeze();
    }
}
