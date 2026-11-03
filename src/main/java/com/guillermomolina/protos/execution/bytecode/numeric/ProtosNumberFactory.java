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

import java.math.BigInteger;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115 LONG-6: GraalPy PFactory-shaped exact numeric construction.
 *
 * <p>The active language Context owns the canonical BigInteger factory.
 * A specialization never walks the guest caller stack or reads a carrier
 * admission certificate to mint a large Integer.
 */
final class ProtosNumberFactory {
    private ProtosNumberFactory() {}

    private static ProtosPrelude prelude(Node node) {
        return ProtosLanguageContext.current(node).numericFactoryPreludeForRuntime();
    }

    @TruffleBoundary
    static Object add(Node node, long left, long right) {
        return ProtosNumericValueSupport.integerFromHost(
                BigInteger.valueOf(left).add(BigInteger.valueOf(right)), prelude(node));
    }

    @TruffleBoundary
    static Object subtract(Node node, long left, long right) {
        return ProtosNumericValueSupport.integerFromHost(
                BigInteger.valueOf(left).subtract(BigInteger.valueOf(right)), prelude(node));
    }

    @TruffleBoundary
    static Object multiply(Node node, long left, long right) {
        return ProtosNumericValueSupport.integerFromHost(
                BigInteger.valueOf(left).multiply(BigInteger.valueOf(right)), prelude(node));
    }

    @TruffleBoundary
    static Object add(Node node, Object left, Object right) {
        return ProtosNumericValueSupport.addIntegers(left, right, prelude(node));
    }

    @TruffleBoundary
    static Object subtract(Node node, Object left, Object right) {
        return ProtosNumericValueSupport.subtractIntegers(left, right, prelude(node));
    }

    @TruffleBoundary
    static Object multiply(Node node, Object left, Object right) {
        return ProtosNumericValueSupport.multiplyIntegers(left, right, prelude(node));
    }
}
