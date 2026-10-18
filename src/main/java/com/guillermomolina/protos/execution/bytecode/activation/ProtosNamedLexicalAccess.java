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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalFallback;
import com.guillermomolina.protos.runtime.ProtosSignalException;

/**
 * I113: the residual name-keyed lexical accesses of an
 * {@code AccessNamedLexical} instruction. Each constant carries its own
 * body, so the call on the builder-fixed constant devirtualizes under
 * partial evaluation.
 */
public enum ProtosNamedLexicalAccess {
    /** The unqualified lookup; signals the lookup Error on absence. */
    LOOKUP {
        @Override
        Object access(ProtosActivation activation, String name) {
            return ProtosLexicalFallback.readByName(activation, name)
                    .orElseThrow(
                            () ->
                                    new ProtosSignalException(
                                            ProtosCoreErrors.newUnqualifiedLookupError(
                                                    activation)));
        }
    },

    /** The captured-binding fallback read. */
    CAPTURED_FALLBACK {
        @Override
        Object access(ProtosActivation activation, String name) {
            return ProtosCapturedLexicalAccess.lookupCapturedFallback(activation, name);
        }
    },

    /** The generic write-target resolution; its assignment is a separate instruction. */
    RESOLVE_WRITE {
        @Override
        Object access(ProtosActivation activation, String name) {
            return ProtosFrameLocalBindings.resolveWritableLexicalTarget(activation, name);
        }
    };

    abstract Object access(ProtosActivation activation, String name);
}
