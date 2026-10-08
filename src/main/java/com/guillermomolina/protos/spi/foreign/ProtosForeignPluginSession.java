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
package com.guillermomolina.protos.spi.foreign;

/**
 * One logical session of an external foreign provider, owned by exactly one Actor (I085-A).
 *
 * <p>Every handle this session answers stays bound to it: Protos passes it back only to
 * operations of this same session and rejects every use after {@link #close()}.
 */
public interface ProtosForeignPluginSession extends AutoCloseable {
    /**
     * Acquires and initializes the module named by {@code canonicalTarget} through the
     * provider's own mechanisms. A thrown exception is an entered foreign failure; a later import
     * of the same target may retry.
     *
     * @return the non-null opaque module handle, classified by the provider's {@link
     *     ProtosForeignValueOperations}; Protos never interprets it on its own
     */
    Object acquireModule(String canonicalTarget) throws Exception;

    @Override
    void close() throws Exception;
}
