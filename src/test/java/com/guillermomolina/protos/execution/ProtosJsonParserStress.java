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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * Explicit JSON parser implementation-shape stress evidence.
 *
 * <p>This class intentionally does not match Surefire's ordinary *Test naming
 * convention. Run it explicitly when validating parser implementation scale.
 */
final class ProtosJsonParserStress {

    @Test
    void deeplyNestedContainersUseExplicitJsonStackRatherThanRecursiveDescent()
            throws Exception {
        int depth = 2048;
        String input = "[".repeat(depth) + "0" + "]".repeat(depth);

        ProtosObjectValue node = ProtosJsonParserModuleTest.parse(input);
        for (int index = 0; index < depth; index++) {
            ProtosArrayValue array =
                    assertInstanceOf(
                            ProtosArrayValue.class,
                            ProtosJsonParserModuleTest.value(node, "array"));
            assertEquals(BigInteger.ONE, array.indexedSize());
            node =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            array.indexedAt(BigInteger.ZERO));
        }
        ProtosJsonParserModuleTest.assertDecimal(node, 0, 0);
    }

    @Test
    void largeArrayUsesBalancedChunkMaterializationAndPreservesEveryElement()
            throws Exception {
        int size = 2048;
        StringBuilder input = new StringBuilder("[");
        for (int index = 0; index < size; index++) {
            if (index != 0) {
                input.append(',');
            }
            input.append(index);
        }
        input.append(']');

        ProtosArrayValue array =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        ProtosJsonParserModuleTest.value(
                                ProtosJsonParserModuleTest.parse(input.toString()),
                                "array"));

        assertTrue(array.isFrozen());
        assertEquals(BigInteger.valueOf(size), array.indexedSize());

        for (int index : new int[] {0, 1, 2, 31, 32, 511, 1024, 2047}) {
            ProtosJsonParserModuleTest.assertDecimal(
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            array.indexedAt(BigInteger.valueOf(index))),
                    index,
                    0);
        }
    }
}
