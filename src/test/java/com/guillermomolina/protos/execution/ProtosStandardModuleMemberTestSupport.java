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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;

/**
 * Reads exact standard-module members through the general Prelude member seam.
 *
 * <p>Installing into a throwaway Context performs no import, module initialization, cache
 * mutation, or source execution; it observes the same frozen member every module instance of the
 * key receives.
 */
final class ProtosStandardModuleMemberTestSupport {
    private ProtosStandardModuleMemberTestSupport() {}

    static ProtosObjectValue member(ProtosPrelude prelude, String moduleId, String name) {
        ProtosObjectValue context = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(new ProtosModuleKey(moduleId), context);
        return (ProtosObjectValue) context.readLocalSlot(name).orElseThrow();
    }

    static ProtosObjectValue bufferedReaderFactory(ProtosPrelude prelude) {
        return member(prelude, "std:io/BufferedReader", "BufferedReader");
    }

    static ProtosObjectValue bufferedWriterFactory(ProtosPrelude prelude) {
        return member(prelude, "std:io/BufferedWriter", "BufferedWriter");
    }
}
