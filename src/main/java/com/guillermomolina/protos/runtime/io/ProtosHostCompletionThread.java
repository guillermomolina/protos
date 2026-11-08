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

/**
 * I156 marker of host threads whose I/O completions must be published inside the owning Actor.
 *
 * <p>A backend that completes operations on a dedicated host thread (the confined Filesystem
 * grant's driver thread) marks that thread once. Flows then deliver such completions as targeted
 * Future completions of the owning Actor domain instead of creating guest values or resolving
 * Futures on the host thread. A completion arriving on any other thread, including the owner
 * itself completing a held backend callback later, is published directly as before.
 */
public final class ProtosHostCompletionThread {
    private static final ThreadLocal<Boolean> MARKED = ThreadLocal.withInitial(() -> false);

    private ProtosHostCompletionThread() {}

    /** Marks the calling thread for its whole lifetime. */
    public static void markCurrent() {
        MARKED.set(true);
    }

    /** Whether the calling thread is a marked host completion thread. */
    public static boolean isCurrent() {
        return MARKED.get();
    }
}
