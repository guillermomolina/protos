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

import com.oracle.truffle.api.exception.AbstractTruffleException;

/**
 * PLAT054 host-facing refusal of a standard Polyglot embedding entry: an invalid Core override, a
 * terminated Process, or an unsafe concurrent RootActor entry.
 *
 * <p>This is not a Protos Error and carries no guest value. It is an {@link
 * AbstractTruffleException} only so the Polyglot host receives it as an ordinary {@code
 * PolyglotException} rather than as an internal implementation failure. No guest code observes it:
 * it is raised only at a host entry boundary, before guest execution begins.
 */
final class ProtosEmbeddingException extends AbstractTruffleException {
    private static final long serialVersionUID = 1L;

    ProtosEmbeddingException(String message) {
        super(message);
    }
}
