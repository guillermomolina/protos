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
import java.io.Closeable;
import java.io.IOException;

/**
 * Host choke point for the physical close of selected File resource custody. Close admission,
 * idempotence, completion callbacks, and lifecycle ordering stay with the caller; only the host
 * close itself is kept out of partial evaluation.
 */
final class ProtosHostResourceClose {
    private ProtosHostResourceClose() {}

    /**
     * Closes {@code resource} on the host. Returns {@code true} on success and {@code false} when
     * the host reports an {@link IOException}; any {@link RuntimeException} propagates unchanged.
     */
    @TruffleBoundary
    static boolean closePhysically(Closeable resource) {
        try {
            resource.close();
            return true;
        } catch (IOException failure) {
            return false;
        }
    }
}
