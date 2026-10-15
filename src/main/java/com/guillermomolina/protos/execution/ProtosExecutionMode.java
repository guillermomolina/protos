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
 * PERF032 execution mode of one RuntimeHost and of every Context it hosts.
 *
 * <p>The mode is selected when the host or Context is created and never changes afterwards. A
 * {@link #NORMAL} execution does not guarantee debugger scopes: its lowering may omit the
 * information they need. A {@link #DEBUG} execution keeps that information from the first call,
 * so a debugger attached at any later point observes exact scopes.
 *
 * <p>I102 (PLAT060-B5): both modes use the same ordinary Closure-call frame-argument ABI. The mode
 * never selects a physical argument layout.
 *
 * <p>I101-A (PLAT058-D): the mode is also the immutable physical compilation profile of every
 * executable owned by the Context. It is passed explicitly to {@link CanonicalToBytecodeLowerer},
 * which emits the proven minimal NORMAL lowering or the complete DEBUG lowering; no emitted
 * instruction selects between them at run time.
 */
enum ProtosExecutionMode {
    NORMAL,
    DEBUG
}
