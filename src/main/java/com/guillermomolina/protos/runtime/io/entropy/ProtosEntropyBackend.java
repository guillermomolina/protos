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

package com.guillermomolina.protos.runtime.io;

/**
 * D243-C host-neutral backend of one explicitly granted Entropy capability.
 *
 * <p>Callbacks carry host-only data and may execute on host threads. They
 * must not construct or access guest objects, Futures, Actors or Contexts.
 * The owning Actor performs the final Protos projection and commitment.
 */
public interface ProtosEntropyBackend {
    int MAX_BYTES = 65536;

    interface Completion {
        /**
         * Reports an independent, complete byte array of exactly the
         * requested length. The receiver must defensively copy it.
         */
        void succeeded(byte[] octets);

        /** Reports failure without exposing native provider details. */
        void failed();
    }

    @FunctionalInterface
    interface Cancellation {
        /** Best-effort cancellation; running provider work need not stop. */
        void cancel();
    }

    /**
     * Submits a positive, already validated byte count.
     *
     * <p>Admission failures may call completion synchronously. No exception
     * from the host provider may escape to Protos.
     */
    Cancellation generate(int count, Completion completion);

    /**
     * Stops new admissions, drops queued work and does not wait for
     * a blocked provider. Idempotent and host-only.
     */
    void close();
}
