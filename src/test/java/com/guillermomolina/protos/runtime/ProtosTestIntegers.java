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

import java.math.BigInteger;

/**
 * Test access to exact semantic Integers across both I091 representations: a
 * {@link ProtosIntegerValue} within the signed-64 range and a {@link ProtosLargeIntegerValue}
 * outside it.
 */
public final class ProtosTestIntegers {
    private ProtosTestIntegers() {}

    /**
     * The normalized Integer denoting {@code value}. A large value delegates to the root object,
     * which suffices for representation-level tests that perform no guest lookup.
     */
    public static Object integer(BigInteger value) {
        return ProtosNumericValueSupport.integerWithPrototype(value, ProtosObjectValue.rootObject());
    }

    /** The normalized Integer denoting {@code value}, owned by {@code prelude}. */
    public static Object integer(BigInteger value, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.integer(value, prelude);
    }

    /** The exact value of a semantic Integer. */
    public static BigInteger exact(Object integer) {
        return ProtosNumericValueSupport.exactBigInteger(integer);
    }
}
