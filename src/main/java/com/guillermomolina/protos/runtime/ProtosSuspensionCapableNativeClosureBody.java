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

import java.util.List;

/**
 * Implementation-only PLAT019 capability carried by a native Closure body.
 *
 * <p>{@link #execute(ProtosActivation, List)} remains the ordinary native path used by the
 * established evaluator and non-Task Bytecode execution. Only a Task-backed C-prime Bytecode
 * invocation may call {@link #executeForBytecodeContinuation(ProtosActivation, List)}.
 *
 * <p>The continuation-capable entry may complete synchronously with an ordinary Protos runtime
 * value or return the backend-private native-suspension descriptor recognized by the Bytecode
 * execution layer. The descriptor itself is deliberately not part of this runtime interface.
 */
public interface ProtosSuspensionCapableNativeClosureBody extends ProtosNativeClosureBody {
    Object executeForBytecodeContinuation(ProtosActivation activation, List<?> supplied);
}
