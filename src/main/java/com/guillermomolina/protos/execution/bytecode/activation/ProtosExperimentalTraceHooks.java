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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * I115-H4-B EXPERIMENTAL internal trace probe (GraalPy trace/profile hooks
 * counterpart), enabled only by the JVM system property {@code
 * protos.i115.trace}, read once at class initialization. It is not a Protos
 * option and not a general tracing facility. Values are reported by Java
 * class name only; no guest value is converted to text.
 */
final class ProtosExperimentalTraceHooks {

    static final boolean ENABLED = Boolean.getBoolean("protos.i115.trace");

    enum Event {
        CALL,
        CALL_RETURN,
        ROOT_RETURN,
        ROOT_EXCEPTION
    }

    private ProtosExperimentalTraceHooks() {}

    @TruffleBoundary
    static void record(Event event, ProtosBytecodeRootNode root, int bytecodeIndex, Object value) {
        if (!ENABLED) {
            return;
        }
        System.err.println("I115_H4B_TRACE=" + event
                + " ROOT=" + (root == null ? "NULL" : root.getName())
                + " BCI=" + bytecodeIndex
                + " VALUE_CLASS=" + (value == null ? "NULL" : value.getClass().getName()));
    }
}
