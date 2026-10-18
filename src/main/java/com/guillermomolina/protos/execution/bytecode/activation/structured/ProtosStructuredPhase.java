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

/**
 * I113: the builder-fixed phase of a shared structured-protocol instruction
 * ({@code StructuredHasNext}, {@code StructuredPrepareChildCall},
 * {@code StructuredAcceptChildOutcome}, {@code StructuredAdvance}). Each
 * operation documents the phases it accepts; a protocol is emitted only with
 * the phases it has.
 */
public enum ProtosStructuredPhase {
    /** The protocol's own step: next position, requirement or release step. */
    STEP,
    /** The protocol's primary guest child. */
    CHILD,
    /** The protocol's second guest child (caseOf action, ensure cleanup, while body). */
    SECONDARY_CHILD,
    /** A Map protocol's guest hash call. */
    HASH,
    /** A Map protocol's guest equality call. */
    EQUALITY,
    /** Entering a Map protocol's guest comparison scope. */
    ENTER_COMPARISON,
    /** Leaving a Map protocol's guest comparison scope. */
    LEAVE_COMPARISON
}
